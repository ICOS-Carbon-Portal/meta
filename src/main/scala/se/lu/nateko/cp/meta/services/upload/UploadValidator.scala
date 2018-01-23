package se.lu.nateko.cp.meta.services.upload

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.DataSubmitterConfig
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.core.data.DataObjectSpec
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.StaticCollectionDto
import akka.NotUsed

class UploadValidator(servers: DataObjectInstanceServers, conf: UploadServiceConfig){
	import servers.metaVocab
	implicit val factory = servers.icosMeta.factory

	private [this] val ok: Try[NotUsed] = Success(NotUsed)

	def validateUpload(meta: UploadMetadataDto, uploader: UserId): Try[NotUsed] = for(
		submConf <- getSubmitterConfig(meta.submitterId);
		_ <- userAuthorizedBySubmitter(submConf, uploader);
		_ <- userAuthorizedByProducer(meta, submConf);
		spec <- servers.getDataObjSpecification(meta.objectSpecification.toRdf);
		_ <- validateForFormat(meta, spec);
		_ <- validatePreviousVersion(meta.isNextVersionOf, spec)
	) yield NotUsed

	def validateCollection(coll: StaticCollectionDto, hash: Sha256Sum, uploader: UserId)(implicit envri: Envri): Try[NotUsed] = for(
		_ <- collMemberListOk(coll, hash);
		submConf <- getSubmitterConfig(coll.submitterId);
		_ <- userAuthorizedBySubmitter(submConf, uploader);
		_ <- submitterAuthorizedByCollectionCreator(submConf, hash);
		_ <- validatePreviousCollectionVersion(coll.isNextVersionOf)
	) yield NotUsed

	def getSubmitterConfig(submitterId: String): Try[DataSubmitterConfig] = {
		conf.submitters.get(submitterId) match {
			case None => Failure(new UploadUserErrorException(s"Unknown submitter: $submitterId"))
			case Some(conf) => Success(conf)
		}
	}

	private def userAuthorizedBySubmitter(submConf: DataSubmitterConfig, uploader: UserId): Try[NotUsed] = {
		val userId = uploader.email
		if(!submConf.authorizedUserIds.contains(userId))
			Failure(new UnauthorizedUploadException(s"User '$userId' is not authorized to upload on behalf of submitter '${submConf.submittingOrganization}'"))
		else ok
	}

	private def submitterAuthorizedByCollectionCreator(submConf: DataSubmitterConfig, coll: Sha256Sum)(implicit envri: Envri): Try[NotUsed] =
		servers.getCollectionCreator(coll).map{creator =>
			if(creator === submConf.submittingOrganization)
				ok
			else
				Failure(new UnauthorizedUploadException(s"Collection already exists and was submitted by $creator, " +
					s"whereas you are submitting on behalf of ${submConf.submittingOrganization}"))
		}.getOrElse(ok)

	private def collMemberListOk(coll: StaticCollectionDto, hash: Sha256Sum)(implicit envri: Envri): Try[NotUsed] = {

		if(!servers.collectionExists(hash) && coll.members.isEmpty)
			Failure(
				new UploadUserErrorException("Creating empty static collections is not allowed")
			)
		else
			coll.members.find{item =>
				!servers.collectionExists(item.toRdf) && !servers.dataObjExists(item.toRdf)
			} match {
				case None => ok
				case Some(item) => Failure(
					new UploadUserErrorException(s"Neither collection nor data object was found at $item")
				)
			}
	}

	private def userAuthorizedByProducer(meta: UploadMetadataDto, submConf: DataSubmitterConfig): Try[Unit] = Try{
		val producer = meta.specificInfo.fold(
			l3 => l3.production.hostOrganization.getOrElse(l3.production.creator),
			_.station
		)

		for(prodOrgClass <- submConf.producingOrganizationClass){
			if(!servers.icosMeta.hasStatement(producer.toRdf, RDF.TYPE, prodOrgClass.toRdf))
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

	private def validateForFormat(meta: UploadMetadataDto, spec: DataObjectSpec): Try[NotUsed] = {
		def hasFormat(format: IRI): Boolean = format === spec.format.uri

		val errors = scala.collection.mutable.Buffer.empty[String]

		meta.specificInfo match{
			case Left(_) =>
				if(spec.dataLevel < 3) errors += "The data level for this kind of metadata package must have been 3"

			case Right(stationMeta) =>
				if(spec.dataLevel > 2) errors += "The data level for this kind of metadata package must have been 2 or less"
				else{
					if(spec.dataLevel <= 1 && stationMeta.acquisitionInterval.isEmpty)
						errors += "Must provide 'aquisitionInterval' with start and stop timestamps."

					if(spec.dataLevel == 2 && stationMeta.nRows.isEmpty && !hasFormat(metaVocab.wdcggFormat))
						errors += "Must provide 'nRows' with number of rows in the uploaded data file."

					if(hasFormat(metaVocab.atcFormat)){
						stationMeta.instrument match{
							case None =>
								errors += "Instrument URL is expected for ATC time series"

							case Some(instrUrl) =>
								val urlPrefix = "http://meta.icos-cp.eu/resources/instruments/ATC_"
								if(!instrUrl.toString.startsWith(urlPrefix))
									errors += s"Instrument URL is expected to start with '$urlPrefix'"
						}
					}
				}
		}

		if(errors.isEmpty) ok
		else Failure(new UploadUserErrorException(errors.mkString("\n")))
	}

	private def validatePreviousVersion(prevVers: Option[Sha256Sum], spec: DataObjectSpec): Try[NotUsed] = {
		prevVers match{
			case None => ok
			case Some(prevHash) => servers.getInstServerForFormat(spec.format.uri.toRdf).flatMap{ server =>
				val dobj = servers.vocab.getDataObject(prevHash)

				if(server.hasStatement(Some(dobj), Some(metaVocab.hasSha256sum), None))
					ok
				else
					Failure(new UploadUserErrorException(s"Previous-version data object was not found: $dobj"))
			}
		}
	}

	private def validatePreviousCollectionVersion(prevVers: Option[Sha256Sum])(implicit envri: Envri): Try[NotUsed] =
		prevVers.map{coll =>
			if(servers.collectionExists(coll))
				ok
			else
				Failure(new UploadUserErrorException(s"Previous-version collection was not found: $coll"))
		}.getOrElse(ok)
}
