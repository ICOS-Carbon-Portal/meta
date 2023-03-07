package se.lu.nateko.cp.meta.services.upload

import akka.NotUsed
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.ConfigLoader
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.DataProductionDto
import se.lu.nateko.cp.meta.DataSubmitterConfig
import se.lu.nateko.cp.meta.DocObjectDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.StationTimeSeriesDto
import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.DataObjectSpec
import se.lu.nateko.cp.meta.core.data.DatasetType
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer.Hash
import se.lu.nateko.cp.meta.utils.*
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import java.time.Instant
import java.util.Date
import scala.collection.mutable.Buffer
import scala.language.strictEquality
import scala.util.Failure
import scala.util.Success
import scala.util.Try

given CanEqual[URI, URI] = CanEqual.derived
given CanEqual[IRI, IRI] = CanEqual.derived
given CanEqual[Instant, Instant] = CanEqual.derived


class UploadValidator(servers: DataObjectInstanceServers){
	import servers.{ metaVocab, vocab }
	given vf: ValueFactory = metaVocab.factory

	private val ok: Try[NotUsed] = Success(NotUsed)

	private type Validated[Dto <: ObjectUploadDto] <: Try[ObjectUploadDto] = Dto match
		case DataObjectDto => Try[DataObjectDto]
		case DocObjectDto => Try[DocObjectDto]

	def validateObject(meta: ObjectUploadDto, uploader: UserId)(using Envri): Try[ObjectUploadDto] = meta match
		case dobj: DataObjectDto => validateDobj(dobj, uploader)
		case doc: DocObjectDto => validateDoc(doc, uploader)


	private def validateDobj(meta: DataObjectDto, uploader: UserId)(using Envri): Try[DataObjectDto] = for(
		submConf <- getSubmitterConfig(meta);
		_ <- userAuthorizedBySubmitter(submConf, uploader);
		_ <- userAuthorizedByProducer(meta, submConf);
		spec <- servers.getDataObjSpecification(meta.objectSpecification.toRdf);
		_ <- userAuthorizedByThemesAndProjects(spec, submConf);
		_ <- validateForFormat(meta, spec, submConf);
		instServer <- servers.getInstServerForFormat(spec.format.uri.toRdf);
		_ <- validatePrevVers(meta, instServer);
		_ <- validateLicence(meta, instServer);
		_ <- growingIsGrowing(meta, spec, instServer, submConf);
		_ <- validateActors(meta, instServer);
		_ <- validateTemporalCoverage(meta, spec);
		_ <- noProductionProvenanceIfL0(meta, spec);
		amended <- validateFileName(meta, instServer);
		_ <- validateMoratorium(amended, instServer);
		_ <- validateDescription(meta.specificInfo.fold(_.description, _.production.flatMap(_.comment)))
	) yield amended

	private def validateDoc(meta: DocObjectDto, uploader: UserId)(using Envri): Try[DocObjectDto] = for(
		submConf <- getSubmitterConfig(meta);
		_ <- userAuthorizedBySubmitter(submConf, uploader);
		instServer <- servers.getDocInstServer;
		_ <- validatePrevVers(meta, instServer);
		amended <- validateFileName(meta, instServer);
		_ <- validateLicence(meta, instServer);
		_ <- validateDescription(meta.description)
	) yield amended


	def validateCollection(coll: StaticCollectionDto, hash: Sha256Sum, uploader: UserId)(using Envri): Try[NotUsed] = for(
		_ <- collMemberListOk(coll, hash);
		submConf <- getSubmitterConfig(coll);
		_ <- validateDescription(coll.description);
		_ <- userAuthorizedBySubmitter(submConf, uploader);
		_ <- submitterAuthorizedByCollectionCreator(submConf, hash);
		_ <- validatePreviousCollectionVersion(coll.isNextVersionOf, hash)
	) yield NotUsed

	def noProductionProvenanceIfL0(meta: DataObjectDto, spec: DataObjectSpec): Try[NotUsed] =
		if spec.dataLevel == 0 && meta.specificInfo.fold(
			spTempMeta => true,
			stationMeta => stationMeta.production.nonEmpty
		) then userFail("Level 0 data object cannot contain production provenance") else ok

	def getSubmitterConfig(dto: UploadDto)(using envri: Envri): Try[DataSubmitterConfig] =
		ConfigLoader.submittersConfig.submitters(envri).get(dto.submitterId) match
			case None => userFail(s"Unknown submitter: ${dto.submitterId}")
			case Some(conf) => Success(conf)


	def updateValidIfObjectNotNew(dto: ObjectUploadDto, submittingOrg: URI)(using Envri): Try[NotUsed] =
		objectKindSameIfNotNew(dto).flatMap(_ => dto match {
			case dobj: DataObjectDto => submitterAndFormatAreSameIfObjectNotNew(dobj, submittingOrg)
			case _: DocObjectDto => submitterIsSameIfObjNotNew(dto, submittingOrg)
		})

	private def objectKindSameIfNotNew(dto: ObjectUploadDto)(using Envri): Try[NotUsed] = dto match{
		case _: DataObjectDto =>
			if(servers.isExistingDocument(dto.hashSum))
				userFail("Cannot accept data object upload as there is already a document object with id " + dto.hashSum.id)
			else Success(NotUsed)
		case _: DocObjectDto =>
			if(servers.isExistingDataObject(dto.hashSum))
				userFail("Cannot accept document object upload as there is already a data object with id " + dto.hashSum.id)
			else Success(NotUsed)
	}

	private def submitterAndFormatAreSameIfObjectNotNew(meta: DataObjectDto, submittingOrg: URI)(using Envri): Try[NotUsed] = {
		val formatValidation: Try[NotUsed] = (
			for(
				newFormat <- servers.getObjSpecificationFormat(meta.objectSpecification.toRdf);
				oldFormat <- servers.getDataObjSpecification(meta.hashSum).flatMap(servers.getObjSpecificationFormat)
			) yield
				if(oldFormat === newFormat) ok else authFail(
					s"Object exists and has format $oldFormat. Upload with format $newFormat is therefore impossible."
				)
		).getOrElse(ok)

		formatValidation.flatMap{_ => submitterIsSameIfObjNotNew(meta, submittingOrg)}
	}

	private def submitterIsSameIfObjNotNew(dto: ObjectUploadDto, submittingOrg: URI)(using Envri): Try[NotUsed] =
		servers.getObjSubmitter(dto).map{subm =>
			if(subm === submittingOrg) ok else authFail(
				s"Object exists and was submitted by $subm. Upload on behalf of $submittingOrg is therefore impossible."
			)
		}.getOrElse(ok)

	private def userAuthorizedBySubmitter(submConf: DataSubmitterConfig, uploader: UserId): Try[NotUsed] = {
		val userId = uploader.email
		if(!submConf.authorizedUserIds.contains(userId))
			authFail(s"User '$userId' is not authorized to upload on behalf of submitter '${submConf.submittingOrganization}'")
		else ok
	}

	private def userAuthorizedByThemesAndProjects(spec: DataObjectSpec, submConf: DataSubmitterConfig): Try[NotUsed] = {
		if(!submConf.authorizedThemes.fold(true)(_.contains(spec.theme.self.uri)))
			authFail(s"Submitter is not authorized to upload data linked to the '${spec.theme.self.uri}' theme")
		else if(!submConf.authorizedProjects.fold(true)(_.contains(spec.project.self.uri)))
			authFail(s"Submitter is not authorized to upload data linked to the '${spec.project.self.uri}' project")
		else ok
	}

	private def submitterAuthorizedByCollectionCreator(submConf: DataSubmitterConfig, coll: Sha256Sum)(using Envri): Try[NotUsed] =
		servers.getCollectionCreator(coll).map{creator =>
			if(creator === submConf.submittingOrganization)
				ok
			else
				authFail(s"Collection already exists and was submitted by $creator, " +
					s"whereas you are submitting on behalf of ${submConf.submittingOrganization}")
		}.getOrElse(ok)

	private def collMemberListOk(coll: StaticCollectionDto, hash: Sha256Sum)(using Envri): Try[NotUsed] = {

		if(!servers.collectionExists(hash) && coll.members.isEmpty)
			userFail("Creating empty static collections is not allowed")
		else
			coll.members.find{item =>
				!servers.collectionExists(item.toRdf) && !servers.dataObjExists(item.toRdf) && !servers.docObjExists(item.toRdf)
			} match {
				case None => ok
				case Some(item) => userFail(s"Neither collection nor object was found at $item")
			}
	}

	private def userAuthorizedByProducer(meta: DataObjectDto, submConf: DataSubmitterConfig)(using envri: Envri): Try[Unit] = Try{
		val producer = meta.specificInfo.fold(
			l3 => l3.production.hostOrganization.getOrElse(l3.production.creator),
			_.station
		)

		for(prodOrgClass <- submConf.producingOrganizationClass){
			if(!servers.metaServers(envri).hasStatement(producer.toRdf, RDF.TYPE, prodOrgClass.toRdf))
				throw new UnauthorizedUploadException(
					s"Data producer '$producer' does not belong to class '$prodOrgClass'"
				)
		}

		for(prodOrg <- submConf.producingOrganization){
			if(producer != prodOrg) throw new UnauthorizedUploadException(
				s"User is not authorized to upload on behalf of producer '$producer'"
			)
		}
	}

	private def validateActors(meta: DataObjectDto, instServ: InstanceServer): Try[NotUsed] =
		import scala.language.implicitConversions
		given Conversion[URI, IRI] = instServ.factory.createIRI(_)

		def isOrg(iri: IRI) = instServ.hasStatement(Some(iri), Some(metaVocab.hasName), None)
		def isPerson(iri: IRI) = instServ.resourceHasType(iri, metaVocab.personClass)
		def isActor(iri: IRI) = isOrg(iri) || isPerson(iri)

		def validate(prod: DataProductionDto): Try[NotUsed] =
			val errors = Buffer.empty[String]
			if !isActor(prod.creator) then errors += s"Invalid creator URL ${prod.creator}"

			for(contributor <- prod.contributors if !isActor(contributor))
				errors += s"Invalid contributor URL $contributor"

			for (org <- prod.hostOrganization if !isOrg(org))
				errors += s"Invalid host organization URL $org"

			if errors.isEmpty then ok else userFail(errors.mkString("\n"))

		meta.specificInfo match
			case Left(spTempMeta) => validate(spTempMeta.production)
			case Right(stationMeta) => stationMeta.production.fold(ok)(validate)


	private def validateTemporalCoverage(meta: DataObjectDto, spec: DataObjectSpec): Try[NotUsed] =

		def validate(interval: TimeInterval): Try[NotUsed] =
			val now = Instant.now()
			if now.compareTo(interval.start) < 0 || now.compareTo(interval.stop) < 0 then
				userFail("Temporal coverage cannot extend into the future")
			else if interval.start.compareTo(interval.stop) > 0 then
				userFail("Start date must come before end date in temporal coverage")
			else ok

		if spec.dataLevel >= 3 then ok else meta.specificInfo match
			case Left(spTempMeta) => validate(spTempMeta.temporal.interval)
			case Right(stationMeta) => stationMeta.acquisitionInterval.fold(ok)(validate)


	private def validateFileName[Dto <: ObjectUploadDto](dto: Dto, instServ: InstanceServer)(using Envri): Validated[Dto] =
		if dto.duplicateFilenameAllowed && !dto.autodeprecateSameFilenameObjects then passValidation(dto)
		else
			val allDuplicates = instServ
				.getStatements(None, Some(metaVocab.hasName), Some(vf.createLiteral(dto.fileName)))
				.collect{case Rdf4jStatement(subj, _, _) => subj}
				.filter(dupIri => !instServ.hasStatement(None, Some(metaVocab.isNextVersionOf), Some(dupIri)))
				.map(_.toJava)
				.collect{case Hash.Object(hash) if hash != dto.hashSum => hash} //can re-upload metadata for existing object
				.toIndexedSeq

			if allDuplicates.isEmpty then passValidation(dto)
			else
				val deprecated = dto.isNextVersionOf.flattenToSeq
				if allDuplicates.exists(deprecated.contains) then passValidation(dto)

				else if dto.autodeprecateSameFilenameObjects then
					withDeprecations(dto, deprecated ++ allDuplicates)

				else failValidation(
					userFail(
						s"File name is already taken by other object(s) (${allDuplicates.mkString(", ")})." +
						" Please deprecate older version(s) or set either 'duplicateFilenameAllowed' or " +
						"'autodeprecateSameFilenameObjects' flag in 'references' to 'true'"
					)
				)
	end validateFileName

	private def withDeprecations[Dto <: ObjectUploadDto](dto: Dto, deprecated: Seq[Sha256Sum]): Validated[Dto] =
		val nextVers: OptionalOneOrSeq[Sha256Sum] = Some(Right(deprecated))
		dto match
			case dobj: DataObjectDto => Success(dobj.copy(isNextVersionOf = nextVers))
			case doc: DocObjectDto => Success(doc.copy(isNextVersionOf = nextVers))

	private def passValidation[Dto <: ObjectUploadDto](dto: Dto): Validated[Dto] = dto match
		case dobj: DataObjectDto => Success(dobj)
		case doc: DocObjectDto => Success(doc)

	private def failValidation[Dto <: ObjectUploadDto](err: Failure[Nothing]) = err.asInstanceOf[Validated[Dto]]

	private def validateMoratorium(meta: DataObjectDto, instServ: InstanceServer)(using Envri): Try[NotUsed] =
		meta.references.flatMap(_.moratorium).fold(ok) {moratorium =>
			val iri = vocab.getStaticObject(meta.hashSum)
			val uploadComplete = instServ.hasStatement(Some(iri), Some(metaVocab.hasSizeInBytes), None)

			def validateMoratorium =
				if moratorium.compareTo(Instant.now()) > 0 then ok
				else userFail("Moratorium date must be in the future")

			if !uploadComplete then validateMoratorium else
				val submissionIri = vocab.getSubmission(meta.hashSum)
				val submissionEndDate = FetchingHelper(instServ).getSingleInstant(submissionIri, metaVocab.prov.endedAtTime)

				val uploadedDobjUnderMoratorium = submissionEndDate.compareTo(Instant.now()) > 0

				if uploadedDobjUnderMoratorium then validateMoratorium
				else userFail("Moratorium only allowed if object has not already been published")
		}

	private def validateDescription(descr: Option[String]): Try[NotUsed] = descr.fold(ok)(
		doc => if (doc.length <= 5000) then ok else userFail("Description is too long, maximum 5000 characters")
	)

	private def validateForFormat(meta: DataObjectDto, spec: DataObjectSpec, subm: DataSubmitterConfig)(using envri: Envri): Try[NotUsed] = {
		def hasFormat(format: IRI): Boolean = format === spec.format.uri

		val errors = scala.collection.mutable.Buffer.empty[String]

		meta.specificInfo match{
			case Left(spTempMeta) =>
				if spec.specificDatasetType != DatasetType.SpatioTemporal
				then errors += "Wrong type of dataset for this object spec (must be spatiotemporal)"
				else
					for(vars <- spTempMeta.variables) spec.datasetSpec.fold[Unit]{
						errors += s"Data object specification ${spec.self.uri} lacks a dataset specification; cannot accept variable info."
					}{dsSpec =>
						val valTypeLookup = servers.metaFetcher.get.getValTypeLookup(dsSpec.self.uri.toRdf)
						vars.foreach{varName =>
							if(valTypeLookup.lookup(varName).isEmpty) errors +=
								s"Variable name '$varName' is not compatible with dataset specification ${dsSpec.self.uri}"
						}
					}

			case Right(stationMeta) =>
				if spec.specificDatasetType != DatasetType.StationTimeSeries
				then errors += "Wrong type of dataset for this object spec (must be station-specific time series)"
				else
					if(spec.datasetSpec.isEmpty && stationMeta.acquisitionInterval.isEmpty)
						errors += "Must provide 'acquisitionInterval' with start and stop timestamps."

					if(
						(spec.datasetSpec.isDefined) && stationMeta.nRows.isEmpty &&
						!hasFormat(metaVocab.wdcggFormat) && !hasFormat(metaVocab.atcProductFormat) && !hasFormat(metaVocab.netCDFTimeSeriesFormat)
					) errors += "Must provide 'nRows' with number of rows in the uploaded data file."

					if(subm.submittingOrganization === vocab.atc){
						val instruments = stationMeta.instruments
						if(instruments.exists(uri => !isAtcInstrument(uri)))
							errors += s"Instrument URL is expected to start with '$atcInstrumentPrefix'"
						if(instruments.isEmpty)
							errors += "Instrument URL(s) expected for ATC time series"
						if(stationMeta.samplingHeight.isEmpty && spec.dataLevel > 0) errors += "Must provide sampling height"
					}

					if (envri == Envri.SITES && stationMeta.site.isEmpty)
						errors += "Must provide 'location/ecosystem'"
		}

		if(errors.isEmpty) ok else userFail(errors.mkString("\n"))
	}

	private val atcInstrumentPrefix = "http://meta.icos-cp.eu/resources/instruments/ATC_"
	private def isAtcInstrument(uri: URI): Boolean = uri.toString.startsWith(atcInstrumentPrefix)

	private def validatePrevVers(dto: ObjectUploadDto, instServ: InstanceServer)(using Envri): Try[NotUsed] = dto
		.isNextVersionOf
		.flattenToSeq
		.map{prevHash =>
			if(prevHash == dto.hashSum)
				userFail("Data/doc object cannot be a next version of itself")

			else
				val prevDobj = vocab.getStaticObject(prevHash)
				bothOk(
					existsAndIsCompleted(prevDobj, instServ),
					{
						val except = vocab.getStaticObject(dto.hashSum)
						hasNoOtherDeprecators(prevDobj, except, instServ, true)
					}
				)
		}
		.foldLeft(ok)(bothOk(_, _))

	private def validateLicence(dto: ObjectUploadDto, instServ: InstanceServer): Try[NotUsed] = {
		dto.references.flatMap(_.licence).fold[Try[NotUsed]](Success(NotUsed)){licUri =>
			if(instServ.getTypes(licUri.toRdf).contains(metaVocab.dcterms.licenseDocClass)) ok
			else userFail(s"Unknown licence $licUri")
		}
	}

	private def existsAndIsCompleted(obj: IRI, inServer: InstanceServer): Try[NotUsed] = {
		if(inServer.hasStatement(Some(obj), Some(metaVocab.hasSizeInBytes), None))
			ok
		else
			userFail(s"Data-/document object was not found or has not been successfully uploaded: $obj")
	}

	private def bothOk(try1: Try[NotUsed], try2: => Try[NotUsed]): Try[NotUsed] = try1.flatMap(_ => try2)

	private def hasNoOtherDeprecators(item: IRI, except: IRI, inServer: InstanceServer, amongCompleted: Boolean): Try[NotUsed] = {

		val deprs = inServer
			.getStatements(None, Some(metaVocab.isNextVersionOf), Some(item))
			.map(_.getSubject)
			.collect{
				case iri: IRI if iri != except => iri
			}.toIndexedSeq

		val filteredDeprs = if(amongCompleted)
				deprs.filter(depr => existsAndIsCompleted(depr, inServer).isSuccess)
			else deprs

		filteredDeprs.headOption.fold(ok){depr =>
			userFail(s"Item $item already has new version $depr; upload your object/collection as new version of the latter instead.")
		}
	}

	private def growingIsGrowing(
		dto: ObjectUploadDto,
		spec: DataObjectSpec,
		server:  InstanceServer,
		subm: DataSubmitterConfig
	)(using Envri): Try[NotUsed] = if(subm.submittingOrganization === vocab.atc) dto match {
		case DataObjectDto(
			_, _, _, _,
			Right(StationTimeSeriesDto(stationUri, _, _, _, _, Some(TimeInterval(_, acqStop)), _, _)),
			Some(Left(prevHash)), _, _
		) =>
			if(spec.dataLevel == 1 && spec.format.uri === metaVocab.atcProductFormat && spec.project.self.uri === vocab.icosProject){
				val prevDobj = vocab.getStaticObject(prevHash)
				val prevAcqStop = server.getUriValues(prevDobj, metaVocab.wasAcquiredBy).flatMap{acq =>
					FetchingHelper(server).getOptionalInstant(acq, metaVocab.prov.endedAtTime)
				}
				if(prevAcqStop.exists(_ == acqStop)) userFail(
					"The supposedly NRT growing data object you intend to upload " +
					"has not grown in comparison with its older version.\n" +
					s"Older object: $prevDobj , station: $stationUri, new (claimed) acquisition stop time: $acqStop"
				) else ok
			} else ok
		case _ => ok
	} else ok

	private def validatePreviousCollectionVersion(prevVers: OptionalOneOrSeq[Sha256Sum], newCollHash: Sha256Sum)(using envri: Envri): Try[NotUsed] =
		prevVers.flattenToSeq.map{coll =>
			if(servers.collectionExists(coll)) bothOk({
				if(coll != newCollHash) ok
				else userFail(s"A collection cannot be a next version of itself")
			},{
				val inServer = servers.collectionServers(envri)
				val prevColl = vocab.getCollection(coll)
				val exceptColl = vocab.getCollection(newCollHash)
				hasNoOtherDeprecators(prevColl, exceptColl, inServer, false)
			}) else
				userFail(s"Previous-version collection was not found: $coll")
		}
		.foldLeft(ok)(bothOk(_, _))

	private def userFail(msg: String) = Failure(new UploadUserErrorException(msg))
	private def authFail(msg: String) = Failure(new UnauthorizedUploadException(msg))
}
