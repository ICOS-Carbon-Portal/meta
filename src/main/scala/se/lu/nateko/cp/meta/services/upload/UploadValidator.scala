package se.lu.nateko.cp.meta.services.upload

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.openrdf.model.URI
import org.openrdf.model.ValueFactory
import org.openrdf.model.vocabulary.RDF
import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.DataSubmitterConfig
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.utils.sesame.javaUriToSesame

class UploadValidator(server: InstanceServer, conf: UploadServiceConfig, vocab: CpmetaVocab){
	implicit val factory = vocab.factory

	def validateUpload(meta: UploadMetadataDto, uploader: UserInfo): Try[Unit] = for(
		submConf <- getSubmitterConfig(meta);
		_ <- userAuthorizedBySubmitter(submConf, uploader);
		_ <- userAuthorizedByProducer(meta, submConf);
		_ <- dataObjectIsNew(meta.hashSum);
		format <- getObjSpecificationFormat(meta.objectSpecification);
		_ <- validateForFormat(meta, format)
	) yield ()

	def getSubmitterConfig(meta: UploadMetadataDto): Try[DataSubmitterConfig] = {
		import meta.submitterId
		conf.submitters.get(submitterId) match {
			case None => Failure(new UploadUserErrorException(s"Unknown submitter: $submitterId"))
			case Some(conf) => Success(conf)
		}
	}

	private def userAuthorizedBySubmitter(submConf: DataSubmitterConfig, uploader: UserInfo): Try[Unit] = {
		val userId = uploader.mail
		if(!submConf.authorizedUserIds.contains(userId))
			Failure(new UnauthorizedUploadException(s"User '$userId' is not authorized to upload on behalf of submitter '${submConf.submittingOrganization}'"))
		else Success(())
	}

	private def userAuthorizedByProducer(meta: UploadMetadataDto, submConf: DataSubmitterConfig): Try[Unit] = Try{
		import meta.producingOrganization

		for(prodOrgClass <- submConf.producingOrganizationClass){
			if(!server.hasStatement(producingOrganization, RDF.TYPE, prodOrgClass))
				throw new UnauthorizedUploadException(
					s"User is not authorized to upload on behalf of producer '$producingOrganization'"
				)
		}

		for(prodOrg <- submConf.producingOrganization){
			if(producingOrganization != prodOrg) throw new UnauthorizedUploadException(
				s"User is not authorized to upload on behalf of producer '$producingOrganization'"
			)
		}
	}

	private def dataObjectIsNew(hashSum: Sha256Sum): Try[Unit] = {
		val objectUri = vocab.getDataObject(hashSum)
		if(server.getStatements(objectUri).nonEmpty)
			Failure(new UploadUserErrorException(s"Upload with hash sum $hashSum has already been registered. Amendments are not supported yet!"))
		else Success(())
	}

	private def getObjSpecificationFormat(objectSpecification: URI): Try[URI] = {
		import InstanceServer.AtMostOne

		server.getUriValues(objectSpecification, vocab.hasFormat, AtMostOne).headOption match {
			case None => Failure(new UploadUserErrorException(s"Object Specification '$objectSpecification' has no format"))
			case Some(uri) => Success(uri)
		}
	}

	private def validateForFormat(meta: UploadMetadataDto, format: URI): Try[Unit] = {
		if(format == vocab.wdcggFormat)
			Success(())
		else if(meta.productionInterval.isDefined)
			Success(())
		else Failure(new UploadUserErrorException("Must provide 'productionInterval' with start and stop timestamps."))
	}

}
