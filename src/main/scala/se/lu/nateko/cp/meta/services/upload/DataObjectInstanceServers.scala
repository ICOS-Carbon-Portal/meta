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
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import org.eclipse.rdf4j.model.vocabulary.RDF

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

	def getDataObjSpecification(objHash: Sha256Sum): Try[IRI] = {
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

	def getInstServerForDataObj(objHash: Sha256Sum): Try[InstanceServer] =
		for(
			objSpec <- getDataObjSpecification(objHash);
			format <- getObjSpecificationFormat(objSpec);
			server <- getInstServerForFormat(format)
		) yield server

	def getCollectionCreator(coll: Sha256Sum)(implicit envri: Envri): Option[IRI] =
		collFetcher.flatMap(_.getCreatorIfCollExists(coll))

	def collectionExists(coll: Sha256Sum)(implicit envri: Envri): Boolean =
		collFetcher.map(_.collectionExists(coll)).getOrElse(false)

	def collectionExists(coll: IRI)(implicit envri: Envri): Boolean =
		collFetcher.map(_.collectionExists(coll)).getOrElse(false)

	private def collFetcher(implicit envri: Envri): Option[CollectionFetcher] = collectionServers.get(envri).map{
		new CollectionFetcher(_, allDataObjs, vocab, metaVocab)
	}

	def dataObjExists(dobj: IRI): Boolean = allDataObjs.hasStatement(dobj, RDF.TYPE, metaVocab.dataObjectClass)

	def getDataObjSubmitter(dobj: Sha256Sum): Option[IRI] = {
		val dataObjUri = vocab.getDataObject(dobj)
		allDataObjs.getUriValues(dataObjUri, metaVocab.wasSubmittedBy).flatMap{subm =>
			allDataObjs.getUriValues(subm, metaVocab.prov.wasAssociatedWith)
		}.headOption
	}
}
