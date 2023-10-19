package se.lu.nateko.cp.meta.services.upload.validation

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
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.core.data.flattenToSeq
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
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.upload.DataObjectInstanceServers
import se.lu.nateko.cp.meta.services.upload.CpmetaFetcher
import eu.icoscp.envri.Envri

given CanEqual[URI, URI] = CanEqual.derived
given CanEqual[IRI, IRI] = CanEqual.derived
given CanEqual[Instant, Instant] = CanEqual.derived

val ok: Try[NotUsed] = Success(NotUsed)
def bothOk(try1: Try[NotUsed], try2: => Try[NotUsed]): Try[NotUsed] = try1.flatMap(_ => try2)
def userFail(msg: String) = Failure(new UploadUserErrorException(msg))
def authFail(msg: String) = Failure(new UnauthorizedUploadException(msg))

class UploadValidator(servers: DataObjectInstanceServers):
	import servers.{ metaVocab, vocab }
	given vf: ValueFactory = metaVocab.factory

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
		instServer <- servers.getInstServerForFormat(spec.format.self.uri.toRdf);
		scoped = ScopedValidator(instServer, vocab);
		_ <- scoped.validatePrevVers(meta);
		_ <- scoped.validateLicence(meta);
		_ <- scoped.growingIsGrowing(meta, spec, submConf);
		_ <- validateActors(meta, instServer);
		_ <- validateTemporalCoverage(meta, spec);
		_ <- validateSpatialCoverage(meta, instServer);
		_ <- noProductionProvenanceIfL0(meta, spec);
		amended <- scoped.validateFileName(meta);
		_ <- scoped.validateMoratorium(amended);
		_ <- validateDescription(meta.specificInfo.fold(_.description, _.production.flatMap(_.comment)))
	) yield amended

	private def validateDoc(meta: DocObjectDto, uploader: UserId)(using Envri): Try[DocObjectDto] = for(
		submConf <- getSubmitterConfig(meta);
		_ <- userAuthorizedBySubmitter(submConf, uploader);
		instServer <- servers.getDocInstServer;
		scoped = ScopedValidator(instServer, vocab);
		_ <- scoped.validatePrevVers(meta);
		amended <- scoped.validateFileName(meta);
		_ <- scoped.validateLicence(meta);
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


	private def validateSpatialCoverage(meta: DataObjectDto, instServ: InstanceServer): Try[NotUsed] =
		def coverageExistsIn(covUri: URI, serv: InstanceServer): Boolean =
			val cov = covUri.toRdf
			serv.hasStatement(Some(cov), Some(metaVocab.asGeoJSON), None) ||
			serv.resourceHasType(cov, metaVocab.latLonBoxClass) ||
			serv.resourceHasType(cov, metaVocab.positionClass)
		def customCoverageExists(covUri: URI) = coverageExistsIn(covUri, instServ.writeContextsView)
		def stockCoverageExists(covUri: URI) = coverageExistsIn(covUri, instServ) && !customCoverageExists(covUri)

		meta.specificInfo match
			case Left(spTemp) => spTemp.spatial match
				case Left(geoFeature) => geoFeature.uri match
					case None => ok
					case Some(covUri) =>
						if customCoverageExists(covUri) then ok
						else userFail(
							"Spatial coverage was supplied with URI for metadata upload, but no prior spatial coverage " +
							"instance appears to exist with this URL in the target RDF graph of this data object. " +
							"If you intend to upload a custom spatial coverage for this object, do not use a URL. " +
							"If you want to reuse a 'stock' spatial coverage, supply a URI only instead of GeoCoverage object."
						)
				case Right(covUri) =>
					if stockCoverageExists(covUri) then ok
					else if customCoverageExists(covUri) then userFail(
						s"There exists a custom spatial coverage with URI $covUri. Please use full GeoFeature object without URI " +
						"inside the metadata upload package, rather than just the URI reference."
					)
					else userFail(s"No 'stock' spatial coverage with URI $covUri")
			case Right(_) => ok


	private def validateDescription(descr: Option[String]): Try[NotUsed] = descr.fold(ok)(
		doc => if (doc.length <= 5000) then ok else userFail("Description is too long, maximum 5000 characters")
	)

	private val formatsWithRowInfoInHeader = Set(
		metaVocab.wdcggFormat, metaVocab.atcProductFormat, metaVocab.netCDFTimeSeriesFormat, metaVocab.asciiAtcFlaskTimeSer
	)

	private def validateForFormat(meta: DataObjectDto, spec: DataObjectSpec, subm: DataSubmitterConfig)(using envri: Envri): Try[NotUsed] = {

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
						!formatsWithRowInfoInHeader.contains(spec.format.self.uri.toRdf)
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


	private def validatePreviousCollectionVersion(prevVers: OptionalOneOrSeq[Sha256Sum], newCollHash: Sha256Sum)(using envri: Envri): Try[NotUsed] =
		prevVers.flattenToSeq.iterator.map{coll =>
			if(servers.collectionExists(coll)) bothOk({
				if(coll != newCollHash) ok
				else userFail(s"A collection cannot be a next version of itself")
			},{
				val inServer = servers.collectionServers(envri)
				val prevColl = vocab.getCollection(coll)
				val exceptColl = vocab.getCollection(newCollHash)
				val scopedValidator = ScopedValidator(inServer, vocab)
				scopedValidator.hasNoOtherDeprecators(prevColl, exceptColl, false, false)
			}) else
				userFail(s"Previous-version collection was not found: $coll")
		}
		.foldLeft(ok)(bothOk(_, _))

end UploadValidator
