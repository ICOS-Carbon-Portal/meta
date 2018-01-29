package se.lu.nateko.cp.meta.services.upload

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.eclipse.rdf4j.model.IRI

import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.core.data.DataObjectSpec
import se.lu.nateko.cp.meta.core.MetaCoreConfig.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.Envri
import org.eclipse.rdf4j.model.vocabulary.RDF
import se.lu.nateko.cp.meta.core.data.Envri.Envri

class DataObjectInstanceServers(
	val icosMeta: InstanceServer,
	val collectionServers: Map[Envri.Value, InstanceServer],
	val allDataObjs: InstanceServer,
	perFormat: Map[IRI, InstanceServer]
)(implicit envriConfs: EnvriConfigs) extends CpmetaFetcher{
	import InstanceServer.AtMostOne

	val metaVocab = new CpmetaVocab(icosMeta.factory)
	val vocab = new CpVocab(icosMeta.factory)
	protected val server = icosMeta

	def getDataObjSpecification(objHash: Sha256Sum)(implicit envri: Envri): Try[IRI] = {
		val dataObjUri = vocab.getDataObject(objHash)

		allDataObjs.getUriValues(dataObjUri, metaVocab.hasObjectSpec, AtMostOne).headOption match {
			case None => Failure(new UploadUserErrorException(s"Object '$objHash' is unknown or has no object specification"))
			case Some(uri) => Success(uri)
		}
	}

	def getObjSpecificationFormat(objectSpecification: IRI): Try[IRI] = {

		icosMeta.getUriValues(objectSpecification, metaVocab.hasFormat, AtMostOne).headOption match {
			case None => Failure(new UploadUserErrorException(s"Object Specification '$objectSpecification' has no format"))
			case Some(uri) => Success(uri)
		}
	}

	def getDataObjSpecification(spec: IRI): Try[DataObjectSpec] = Try{getSpecification(spec)}

	def getInstServerForFormat(format: IRI): Try[InstanceServer] = {
		perFormat.get(format) match{
			case None => Failure(new UploadUserErrorException(s"Format '$format' has no instance server configured for it"))
			case Some(server) => Success(server)
		}
	}

	def getInstServerForDataObj(objHash: Sha256Sum)(implicit envri: Envri): Try[InstanceServer] =
		for(
			objSpec <- getDataObjSpecification(objHash);
			format <- getObjSpecificationFormat(objSpec);
			server <- getInstServerForFormat(format)
		) yield server

	def getCollectionCreator(coll: Sha256Sum)(implicit envri: Envri.Value): Option[IRI] = collectionServers.get(envri).flatMap{
		_.getUriValues(vocab.getCollection(coll), metaVocab.dcterms.creator, AtMostOne).headOption
	}

	def collectionExists(coll: Sha256Sum)(implicit envri: Envri.Value): Boolean = collectionExists(vocab.getCollection(coll))

	def collectionExists(coll: IRI)(implicit envri: Envri.Value): Boolean = collectionServers.get(envri)
		.map{_.hasStatement(coll, RDF.TYPE, metaVocab.collectionClass)}.getOrElse(false)

	def dataObjExists(dobj: IRI): Boolean = allDataObjs.hasStatement(dobj, RDF.TYPE, metaVocab.dataObjectClass)
}
