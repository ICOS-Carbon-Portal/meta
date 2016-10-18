package se.lu.nateko.cp.meta.services.upload

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.openrdf.model.URI
import org.openrdf.model.ValueFactory
import org.openrdf.model.vocabulary.RDF
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.DataSubmitterConfig
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.core.data.DataObjectSpec

class UploadValidator(servers: DataObjectInstanceServers, conf: UploadServiceConfig){
	import servers.metaVocab
	implicit val factory = servers.icosMeta.factory

	def validateUpload(meta: UploadMetadataDto, uploader: UserId): Try[Unit] = for(
		submConf <- getSubmitterConfig(meta);
		_ <- userAuthorizedBySubmitter(submConf, uploader);
		_ <- userAuthorizedByProducer(meta, submConf);
		_ <- dataObjectIsNew(meta.hashSum);
		spec <- servers.getDataObjSpecification(meta.objectSpecification);
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
			if(!servers.icosMeta.hasStatement(producer, RDF.TYPE, prodOrgClass))
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

	private def dataObjectIsNew(hashSum: Sha256Sum): Try[Unit] = {
		if(servers.dataObjectIsKnown(hashSum))
			Failure(new UploadUserErrorException(s"Upload with hash sum $hashSum has already been registered. Amendments are not supported yet!"))
		else Success(())
	}

	private def validateForFormat(meta: UploadMetadataDto, spec: DataObjectSpec): Try[Unit] = {
		def hasFormat(format: URI): Boolean = spec.format.uri == format.toJava

		if(hasFormat(metaVocab.wdcggFormat) || spec.dataLevel == 3)
			Success(())
		else {
			val stationMetaOpt = meta.specificInfo.right.toOption
			val acqInterval = stationMetaOpt.flatMap(_.acquisitionInterval)
			val nRows = stationMetaOpt.flatMap(_.nRows)
			if(acqInterval.isEmpty && !hasFormat(metaVocab.etcFormat))
				Failure(new UploadUserErrorException("Must provide 'aquisitionInterval' with start and stop timestamps."))
			else if(nRows.isEmpty && spec.dataLevel == 2)
				Failure(new UploadUserErrorException("Must provide 'nRows' with number of rows in the uploaded data file."))
			else Success(())
		}
	}

}
