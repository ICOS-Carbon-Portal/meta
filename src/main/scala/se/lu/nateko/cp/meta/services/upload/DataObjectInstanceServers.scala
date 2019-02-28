package se.lu.nateko.cp.meta.services.upload

import scala.util.Try

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF

import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.DocObjectDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{ DataObjectSpec, Station }
import se.lu.nateko.cp.meta.core.data.Envri.{ Envri, EnvriConfigs }
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.InstanceServer.AtMostOne
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.utils._

class DataObjectInstanceServers(
	val metaServers: Map[Envri, InstanceServer],
	val collectionServers: Map[Envri, InstanceServer],
	docServers: Map[Envri, InstanceServer],
	val allDataObjs: Map[Envri, InstanceServer],
	perFormat: Map[IRI, InstanceServer]
)(implicit envriConfs: EnvriConfigs, factory: ValueFactory){top =>

	val metaVocab = new CpmetaVocab(factory)
	val vocab = new CpVocab(factory)

	private val metaFetchers = metaServers.map{case (envri, instServer) =>
		envri -> new CpmetaFetcher{
			protected val server = instServer
			val vocab = top.vocab
		}
	}

	def getStation(station: IRI)(implicit envri: Envri): Option[Station] = {
		metaFetchers(envri).getOptionalStation(station)
	}

	def getDataObjSpecification(objHash: Sha256Sum)(implicit envri: Envri): Try[IRI] = {
		val dataObjUri = vocab.getStaticObject(objHash)

		allDataObjs(envri).getUriValues(dataObjUri, metaVocab.hasObjectSpec, AtMostOne).headOption.toTry{
			new UploadUserErrorException(s"Object '$objHash' is unknown or has no object specification")
		}
	}

	def getObjSpecificationFormat(objectSpecification: IRI)(implicit envri: Envri): Try[IRI] =
		metaFetchers(envri).getOptionalSpecificationFormat(objectSpecification).toTry{
			new UploadUserErrorException(s"Object Specification '$objectSpecification' has no format")
		}


	def getDataObjSpecification(spec: IRI)(implicit envri: Envri): Try[DataObjectSpec] = Try{
		metaFetchers(envri).getSpecification(spec)
	}

	def getInstServerForFormat(format: IRI): Try[InstanceServer] = perFormat.get(format).toTry{
		new UploadUserErrorException(s"Format '$format' has no instance server configured for it")
	}

	def getInstServerForDataObj(objHash: Sha256Sum)(implicit envri: Envri): Try[InstanceServer] =
		for(
			objSpec <- getDataObjSpecification(objHash);
			format <- getObjSpecificationFormat(objSpec);
			server <- getInstServerForFormat(format)
		) yield server

	def getDocInstServer(implicit envri: Envri): Try[InstanceServer] = docServers.get(envri).toTry{
		new UploadUserErrorException(s"ENVRI '$envri' has no document instance server configured for it")
	}

	def getCollectionCreator(coll: Sha256Sum)(implicit envri: Envri): Option[IRI] =
		collFetcher.flatMap(_.getCreatorIfCollExists(coll))

	def collectionExists(coll: Sha256Sum)(implicit envri: Envri): Boolean =
		collFetcher.map(_.collectionExists(coll)).getOrElse(false)

	def collectionExists(coll: IRI)(implicit envri: Envri): Boolean =
		collFetcher.map(_.collectionExists(coll)).getOrElse(false)

	def collFetcher(implicit envri: Envri): Option[CollectionFetcher] = collectionServers.get(envri).map{
		new CollectionFetcher(_, allDataObjs(envri), vocab)
	}

	def dataObjExists(dobj: IRI)(implicit envri: Envri): Boolean =
		allDataObjs(envri).hasStatement(dobj, RDF.TYPE, metaVocab.dataObjectClass)

	def getObjSubmitter(obj: ObjectUploadDto)(implicit envri: Envri): Option[IRI] = {
		val objUri = vocab.getStaticObject(obj.hashSum)
		val server = obj match{
			case _: DataObjectDto => allDataObjs(envri)
			case _: DocObjectDto => getDocInstServer.get
		}
		server.getUriValues(objUri, metaVocab.wasSubmittedBy).flatMap{subm =>
			server.getUriValues(subm, metaVocab.prov.wasAssociatedWith)
		}.headOption
	}
}
