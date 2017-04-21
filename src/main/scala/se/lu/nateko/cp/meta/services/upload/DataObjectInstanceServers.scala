package se.lu.nateko.cp.meta.services.upload

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.openrdf.model.URI

import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.core.data.DataObjectSpec

class DataObjectInstanceServers(
	val icosMeta: InstanceServer,
	allDataObjs: InstanceServer,
	perFormat: Map[URI, InstanceServer]
) extends CpmetaFetcher{
	import InstanceServer.AtMostOne

	val metaVocab = new CpmetaVocab(icosMeta.factory)
	val vocab = new CpVocab(icosMeta.factory)
	protected val server = icosMeta

	def getDataObjSpecification(objHash: Sha256Sum): Try[URI] = {
		val dataObjUri = vocab.getDataObject(objHash)

		allDataObjs.getUriValues(dataObjUri, metaVocab.hasObjectSpec, AtMostOne).headOption match {
			case None => Failure(new UploadUserErrorException(s"Object '$objHash' is unknown or has no object specification"))
			case Some(uri) => Success(uri)
		}
		
	}

	def getObjSpecificationFormat(objectSpecification: URI): Try[URI] = {

		icosMeta.getUriValues(objectSpecification, metaVocab.hasFormat, AtMostOne).headOption match {
			case None => Failure(new UploadUserErrorException(s"Object Specification '$objectSpecification' has no format"))
			case Some(uri) => Success(uri)
		}
	}

	def getDataObjSpecification(spec: URI): Try[DataObjectSpec] = Try{getSpecification(spec)}

	def getInstServerForFormat(format: URI): Try[InstanceServer] = {
		perFormat.get(format) match{
			case None => Failure(new UploadUserErrorException(s"Format '$format' has no instance server configured for it"))
			case Some(server) => Success(server)
		}
	}

	def getInstServerForDataObj(objHash: Sha256Sum): Try[InstanceServer] =
		for(
			objSpec <- getDataObjSpecification(objHash);
			format <- getObjSpecificationFormat(objSpec);
			server <- getInstServerForFormat(format)
		) yield server

}
