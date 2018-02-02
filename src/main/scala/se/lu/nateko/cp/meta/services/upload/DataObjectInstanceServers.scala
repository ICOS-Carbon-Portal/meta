package se.lu.nateko.cp.meta.services.upload

import scala.util.Try

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF

import se.lu.nateko.cp.meta.core.MetaCoreConfig.EnvriConfigs
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.DataObjectSpec
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.InstanceServer.AtMostOne
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.utils._

class DataObjectInstanceServers(
	val metaServers: Map[Envri, InstanceServer],
	val collectionServers: Map[Envri, InstanceServer],
	val allDataObjs: InstanceServer,
	perFormat: Map[IRI, InstanceServer]
)(implicit envriConfs: EnvriConfigs){

	val metaVocab = new CpmetaVocab(allDataObjs.factory)
	val vocab = new CpVocab(allDataObjs.factory)

	private val metaFetchers = metaServers.map{case (envri, instServer) =>
		envri -> new CpmetaFetcher{
			protected val server = instServer
			protected val vocab = new CpVocab(instServer.factory)
			protected val metaVocab = new CpmetaVocab(instServer.factory)
		}
	}
	private def metaFetcher(implicit envri: Envri): CpmetaFetcher = metaFetchers(envri)

	def getDataObjSpecification(objHash: Sha256Sum)(implicit envri: Envri): Try[IRI] = {
		val dataObjUri = vocab.getDataObject(objHash)

		allDataObjs.getUriValues(dataObjUri, metaVocab.hasObjectSpec, AtMostOne).headOption.toTry{
			new UploadUserErrorException(s"Object '$objHash' is unknown or has no object specification")
		}
	}

	def getObjSpecificationFormat(objectSpecification: IRI)(implicit envri: Envri): Try[IRI] =
		metaFetcher.getOptionalSpecificationFormat(objectSpecification).toTry{
			new UploadUserErrorException(s"Object Specification '$objectSpecification' has no format")
		}


	def getDataObjSpecification(spec: IRI)(implicit envri: Envri): Try[DataObjectSpec] = Try{metaFetcher.getSpecification(spec)}

	def getInstServerForFormat(format: IRI): Try[InstanceServer] = perFormat.get(format).toTry{
		new UploadUserErrorException(s"Format '$format' has no instance server configured for it")
	}

	def getInstServerForDataObj(objHash: Sha256Sum)(implicit envri: Envri): Try[InstanceServer] =
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

	def getDataObjSubmitter(dobj: Sha256Sum)(implicit envri: Envri): Option[IRI] = {
		val dataObjUri = vocab.getDataObject(dobj)
		allDataObjs.getUriValues(dataObjUri, metaVocab.wasSubmittedBy).flatMap{subm =>
			allDataObjs.getUriValues(subm, metaVocab.prov.wasAssociatedWith)
		}.headOption
	}
}
