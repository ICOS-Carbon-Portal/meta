package se.lu.nateko.cp.meta.services.upload

import java.net.URI

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF

import akka.NotUsed
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.DataSubmitterConfig
import se.lu.nateko.cp.meta.DocObjectDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.DataObjectSpec
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.utils._
import se.lu.nateko.cp.meta.StationDataMetadata
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.ConfigLoader

class UploadValidator(servers: DataObjectInstanceServers){
	import servers.{ metaVocab, vocab }
	implicit val factory = metaVocab.factory

	private [this] val ok: Try[NotUsed] = Success(NotUsed)

	def validateObject(meta: ObjectUploadDto, uploader: UserId)(implicit envri: Envri): Try[NotUsed] = meta match {
		case dobj: DataObjectDto => validateDobj(dobj, uploader)
		case doc: DocObjectDto => validateDoc(doc, uploader)
	}

	private def validateDobj(meta: DataObjectDto, uploader: UserId)(implicit envri: Envri): Try[NotUsed] = for(
		submConf <- getSubmitterConfig(meta);
		_ <- userAuthorizedBySubmitter(submConf, uploader);
		_ <- userAuthorizedByProducer(meta, submConf);
		spec <- servers.getDataObjSpecification(meta.objectSpecification.toRdf);
		_ <- userAuthorizedByThemesAndProjects(spec, submConf);
		_ <- validateForFormat(meta, spec, submConf);
		_ <- validatePrevVers(meta, getInstServer(spec));
		_ <- growingIsGrowing(meta, spec, getInstServer(spec), submConf)
	) yield NotUsed

	private def validateDoc(meta: DocObjectDto, uploader: UserId)(implicit envri: Envri): Try[NotUsed] = for(
		submConf <- getSubmitterConfig(meta);
		_ <- userAuthorizedBySubmitter(submConf, uploader);
		_ <- validatePrevVers(meta, servers.getDocInstServer)
	) yield NotUsed

	private def getInstServer(spec: DataObjectSpec)(implicit envri: Envri): Try[InstanceServer] =
		servers.getInstServerForFormat(spec.format.uri.toRdf)

	def validateCollection(coll: StaticCollectionDto, hash: Sha256Sum, uploader: UserId)(implicit envri: Envri): Try[NotUsed] = for(
		_ <- collMemberListOk(coll, hash);
		submConf <- getSubmitterConfig(coll);
		_ <- userAuthorizedBySubmitter(submConf, uploader);
		_ <- submitterAuthorizedByCollectionCreator(submConf, hash);
		_ <- validatePreviousCollectionVersion(coll.isNextVersionOf, hash)
	) yield NotUsed

	def getSubmitterConfig(dto: UploadDto)(implicit envri: Envri): Try[DataSubmitterConfig] = {
		ConfigLoader.submittersConfig.submitters(envri).get(dto.submitterId) match {
			case None => userFail(s"Unknown submitter: ${dto.submitterId}")
			case Some(conf) => Success(conf)
		}
	}

	def updateValidIfObjectNotNew(dto: ObjectUploadDto, submittingOrg: URI)(implicit envri: Envri): Try[NotUsed] =
		objectKindSameIfNotNew(dto).flatMap(_ => dto match {
			case dobj: DataObjectDto => submitterAndFormatAreSameIfObjectNotNew(dobj, submittingOrg)
			case _: DocObjectDto => submitterIsSameIfObjNotNew(dto, submittingOrg)
		})

	private def objectKindSameIfNotNew(dto: ObjectUploadDto)(implicit envri: Envri): Try[NotUsed] = dto match{
		case _: DataObjectDto =>
			if(servers.isExistingDocument(dto.hashSum))
				userFail("Cannot accept data object upload as there is already a document object with id " + dto.hashSum.id)
			else Success(NotUsed)
		case _: DocObjectDto =>
			if(servers.isExistingDataObject(dto.hashSum))
				userFail("Cannot accept document object upload as there is already a data object with id " + dto.hashSum.id)
			else Success(NotUsed)
	}

	private def submitterAndFormatAreSameIfObjectNotNew(meta: DataObjectDto, submittingOrg: URI)(implicit envri: Envri): Try[NotUsed] = {
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

	private def submitterIsSameIfObjNotNew(dto: ObjectUploadDto, submittingOrg: URI)(implicit envri: Envri): Try[NotUsed] =
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

	private def submitterAuthorizedByCollectionCreator(submConf: DataSubmitterConfig, coll: Sha256Sum)(implicit envri: Envri): Try[NotUsed] =
		servers.getCollectionCreator(coll).map{creator =>
			if(creator === submConf.submittingOrganization)
				ok
			else
				authFail(s"Collection already exists and was submitted by $creator, " +
					s"whereas you are submitting on behalf of ${submConf.submittingOrganization}")
		}.getOrElse(ok)

	private def collMemberListOk(coll: StaticCollectionDto, hash: Sha256Sum)(implicit envri: Envri): Try[NotUsed] = {

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

	private def userAuthorizedByProducer(meta: DataObjectDto, submConf: DataSubmitterConfig)(implicit envri: Envri): Try[Unit] = Try{
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

	private def validateForFormat(meta: DataObjectDto, spec: DataObjectSpec, subm: DataSubmitterConfig)(implicit envri: Envri): Try[NotUsed] = {
		def hasFormat(format: IRI): Boolean = format === spec.format.uri

		val errors = scala.collection.mutable.Buffer.empty[String]

		meta.specificInfo match{
			case Left(l3meta) =>
				for(vars <- l3meta.variables) spec.datasetSpec.fold[Unit]{
					errors += s"Data object specification ${spec.self.uri} lacks a dataset specification; cannot accept variable info."
				}{dsSpec =>
					val valTypeLookup = servers.metaFetchers(envri).getValTypeLookup(dsSpec.self.uri.toRdf)
					vars.foreach{varName =>
						if(valTypeLookup.lookup(varName).isEmpty) errors +=
							s"Variable name '$varName' is not compatible with dataset specification ${dsSpec.self.uri}"
					}
				}
				if(spec.dataLevel < 3) errors += "The data level for this kind of metadata package must have been 3"

			case Right(stationMeta) =>
				if(spec.dataLevel > 2) errors += "The data level for this kind of metadata package must have been 2 or less"
				else{
					if(spec.datasetSpec.isEmpty && stationMeta.acquisitionInterval.isEmpty)
						errors += "Must provide 'acquisitionInterval' with start and stop timestamps."

					if(
						(spec.datasetSpec.isDefined) && stationMeta.nRows.isEmpty &&
						!hasFormat(metaVocab.wdcggFormat) && !hasFormat(metaVocab.atcProductFormat)
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
		}

		if(errors.isEmpty) ok else userFail(errors.mkString("\n"))
	}

	private val atcInstrumentPrefix = "http://meta.icos-cp.eu/resources/instruments/ATC_"
	private def isAtcInstrument(uri: URI): Boolean = uri.toString.startsWith(atcInstrumentPrefix)

	private def validatePrevVers(dto: ObjectUploadDto, getInstServ: => Try[InstanceServer])(implicit envri: Envri): Try[NotUsed] = dto
		.isNextVersionOf
		.flattenToSeq
		.map{prevHash =>
			if(prevHash == dto.hashSum)
				userFail("Data/doc object cannot be a next version of itself")

			else getInstServ.flatMap{ inServer =>
				val prevDobj = vocab.getStaticObject(prevHash)
				bothOk(
					existsAndIsCompleted(prevDobj, inServer),
					{
						val except = vocab.getStaticObject(dto.hashSum)
						hasNoOtherDeprecators(prevDobj, except, inServer, true)
					}
				)
			}
		}
		.foldLeft(ok)(bothOk(_, _))

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
		server:  => Try[InstanceServer],
		subm: DataSubmitterConfig
	)(implicit envri: Envri): Try[NotUsed] = if(subm.submittingOrganization === vocab.atc) dto match {
		case DataObjectDto(
			_, _, _, _,
			Right(StationDataMetadata(stationUri, _, _, _, _, Some(TimeInterval(_, acqStop)), _, _)),
			Some(Left(prevHash)), _, _
		) =>
			if(spec.dataLevel == 1 && spec.format.uri === metaVocab.atcProductFormat && spec.project.self.uri === vocab.icosProject){
				val prevDobj = vocab.getStaticObject(prevHash)
				server.flatMap{instServer =>
					val prevAcqStop = instServer.getUriValues(prevDobj, metaVocab.wasAcquiredBy).flatMap{acq =>
						FetchingHelper(instServer).getOptionalInstant(acq, metaVocab.prov.endedAtTime)
					}
					if(prevAcqStop.exists(_ == acqStop)) userFail(
						"The supposedly NRT growing data object you intend to upload " +
						"has not grown in comparison with its older version.\n" +
						s"Older object: $prevDobj , station: $stationUri, new (claimed) acquisition stop time: $acqStop"
					) else ok
				}
			} else ok
		case _ => ok
	} else ok

	private def validatePreviousCollectionVersion(prevVers: OptionalOneOrSeq[Sha256Sum], newCollHash: Sha256Sum)(implicit envri: Envri): Try[NotUsed] =
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
