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
import scala.util.Success
import se.lu.nateko.cp.meta.instanceserver.CompositeReadonlyInstanceServer

class DataObjectInstanceServers(
	val metaServers: Map[Envri, InstanceServer],
	val collectionServers: Map[Envri, InstanceServer],
	docServers: Map[Envri, InstanceServer],
	val allDataObjs: Map[Envri, InstanceServer],
	perFormat: Map[Envri, Map[IRI, InstanceServer]]
)(implicit envriConfs: EnvriConfigs, factory: ValueFactory){

	val metaVocab = new CpmetaVocab(factory)
	val vocab = new CpVocab(factory)

	val metaFetchers = metaServers.map{case (envri, instServer) =>
		envri -> new CpmetaFetcher{
			override protected val server = instServer
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
		val fetcher = plainFetcher.getOrElse(throw new UploadUserErrorException(s"ENVRI $envri unknown or not configured properly"))
		metaFetchers(envri).getSpecification(spec, fetcher)
	}

	def getInstServerForFormat(format: IRI)(implicit envri: Envri): Try[InstanceServer] =
		perFormat.get(envri).flatMap(_.get(format)).toTry{
			new UploadUserErrorException(s"ENVRI $envri unknown or has no instance server configured for format '$format'")
		}

	def getInstServerForStaticObj(objHash: Sha256Sum)(implicit envri: Envri): Try[InstanceServer] = docServers
		.get(envri)
		.filter{
			_.hasStatement(
				vocab.getStaticObject(objHash), RDF.TYPE, metaVocab.docObjectClass
			)
		}
		.fold(getInstServerForDataObj(objHash: Sha256Sum))(Success(_))

	def isExistingDataObject(hash: Sha256Sum)(implicit envri: Envri): Boolean =
		getInstServerForDataObj(hash).map{
			_.hasStatement(
				vocab.getStaticObject(hash), RDF.TYPE, metaVocab.dataObjectClass
			)
		}.getOrElse(false)

	def isExistingDocument(hash: Sha256Sum)(implicit envri: Envri): Boolean =
		docServers.get(envri).map{
			_.hasStatement(
				vocab.getStaticObject(hash), RDF.TYPE, metaVocab.docObjectClass
			)
		}.getOrElse(false)

	private def getInstServerForDataObj(objHash: Sha256Sum)(implicit envri: Envri): Try[InstanceServer] =
		for(
			objSpec <- getDataObjSpecification(objHash);
			format <- getObjSpecificationFormat(objSpec);
			server <- getInstServerForFormat(format)
		) yield server

	def getDocInstServer(implicit envri: Envri): Try[InstanceServer] = docServers.get(envri).toTry{
		new UploadUserErrorException(s"ENVRI '$envri' has no document instance server configured for it")
	}

	def getCollectionCreator(coll: Sha256Sum)(implicit envri: Envri): Option[IRI] =
		collFetcherLite.flatMap(_.getCreatorIfCollExists(coll))

	def collectionExists(coll: Sha256Sum)(implicit envri: Envri): Boolean =
		collFetcherLite.map(_.collectionExists(coll)).getOrElse(false)

	def collectionExists(coll: IRI)(implicit envri: Envri): Boolean =
		collFetcherLite.map(_.collectionExists(coll)).getOrElse(false)

	def plainFetcher(implicit envri: Envri): Option[PlainStaticObjectFetcher] = for(
			objsServ <- allDataObjs.get(envri);
			docsServ <- docServers.get(envri)
		) yield{
			val joint = new CompositeReadonlyInstanceServer(objsServ, docsServ)
			new PlainStaticObjectFetcher(joint)
		}

	def collFetcher(implicit envri: Envri): Option[CollectionFetcher] = for(
		collServer <- collectionServers.get(envri);
		thePlainFetcher <- plainFetcher
	) yield new CollectionFetcher(collServer, thePlainFetcher, vocab)

	def collFetcherLite(implicit envri: Envri): Option[CollectionFetcherLite] = collectionServers.get(envri)
		.map(new CollectionFetcherLite(_, vocab))

	def dataObjExists(dobj: IRI)(implicit envri: Envri): Boolean =
		allDataObjs(envri).hasStatement(dobj, RDF.TYPE, metaVocab.dataObjectClass)

	def docObjExists(dobj: IRI)(implicit envri: Envri): Boolean =
		docServers(envri).hasStatement(dobj, RDF.TYPE, metaVocab.docObjectClass)

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
