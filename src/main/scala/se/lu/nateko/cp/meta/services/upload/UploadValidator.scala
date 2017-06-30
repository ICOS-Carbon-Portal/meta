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

class UploadValidator(servers: DataObjectInstanceServers, conf: UploadServiceConfig){
	import servers.metaVocab
	implicit val factory = servers.icosMeta.factory

	def validateUpload(meta: UploadMetadataDto, uploader: UserId): Try[Unit] = for(
		submConf <- getSubmitterConfig(meta);
		_ <- userAuthorizedBySubmitter(submConf, uploader);
		_ <- userAuthorizedByProducer(meta, submConf);
		spec <- servers.getDataObjSpecification(meta.objectSpecification.toRdf);
		_ <- validateForFormat(meta, spec)
	) yield ()

	def getSubmitterConfig(meta: UploadMetadataDto): Try[DataSubmitterConfig] = {
		import meta.submitterId
		conf.submitters.get(submitterId) match {
			case None => Failure(new UploadUserErrorException(s"Unknown submitter: $submitterId"))
			case Some(conf) => Success(conf)
		}
	}

	private def userAuthorizedBySubmitter(submConf: DataSubmitterConfig, uploader: UserId): Try[Unit] = {
		val userId = uploader.email
		if(!submConf.authorizedUserIds.contains(userId))
			Failure(new UnauthorizedUploadException(s"User '$userId' is not authorized to upload on behalf of submitter '${submConf.submittingOrganization}'"))
		else Success(())
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

	private def validateForFormat(meta: UploadMetadataDto, spec: DataObjectSpec): Try[Unit] = {
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

		if(errors.isEmpty) Success(())
		else Failure(new UploadUserErrorException(errors.mkString("\n")))
	}

}
