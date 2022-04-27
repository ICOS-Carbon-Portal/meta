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
import se.lu.nateko.cp.meta.services.citation.CitationMaker

class DataObjectInstanceServers(
	val metaServers: Map[Envri, InstanceServer],
	val collectionServers: Map[Envri, InstanceServer],
	docServers: Map[Envri, InstanceServer],
	val allDataObjs: Map[Envri, InstanceServer],
	perFormat: Map[Envri, Map[IRI, InstanceServer]]
)(implicit envriConfs: EnvriConfigs, factory: ValueFactory){dois =>

	val metaVocab = new CpmetaVocab(factory)
	val vocab = new CpVocab(factory)

	val metaFetchers = metaServers.flatMap{case (envri, instServer) =>
		for(
			objsServ <- allDataObjs.get(envri);
			docsServ <- docServers.get(envri)
		) yield{
			val joint = new CompositeReadonlyInstanceServer(objsServ, docsServ)
			envri -> new DobjMetaFetcher{
				override val server = instServer
				override protected val vocab = dois.vocab
				override val plainObjFetcher = new PlainStaticObjectFetcher(joint)
			}
		}
	}

	def metaFetcher(using envri: Envri) = metaFetchers.get(envri).toTry{
		new UploadUserErrorException(s"ENVRI $envri unknown or not configured properly")
	}

	def getStation(station: IRI)(using Envri): Try[Option[Station]] = metaFetcher
		.flatMap(_.getOptionalStation(station))

	def getDataObjSpecification(objHash: Sha256Sum)(using envri: Envri): Try[IRI] = {
		val dataObjUri = vocab.getStaticObject(objHash)

		allDataObjs(envri).getUriValues(dataObjUri, metaVocab.hasObjectSpec, AtMostOne).headOption.toTry{
			new UploadUserErrorException(s"Object '$objHash' is unknown or has no object specification")
		}
	}

	def getObjSpecificationFormat(objectSpecification: IRI)(using Envri): Try[IRI] = metaFetcher.flatMap{
		_.getOptionalSpecificationFormat(objectSpecification).toTry{
			new UploadUserErrorException(s"Object Specification '$objectSpecification' has no format")
		}
	}

	def getDataObjSpecification(spec: IRI)(using Envri): Try[DataObjectSpec] = metaFetcher.map(_.getSpecification(spec))

	def getInstServerForFormat(format: IRI)(using envri: Envri): Try[InstanceServer] =
		perFormat.get(envri).flatMap(_.get(format)).toTry{
			new UploadUserErrorException(s"ENVRI $envri unknown or has no instance server configured for format '$format'")
		}

	def getInstServerForStaticObj(objHash: Sha256Sum)(using envri: Envri): Try[InstanceServer] = docServers
		.get(envri)
		.filter{
			_.hasStatement(
				vocab.getStaticObject(objHash), RDF.TYPE, metaVocab.docObjectClass
			)
		}
		.fold(getInstServerForDataObj(objHash: Sha256Sum))(Success(_))

	def isExistingDataObject(hash: Sha256Sum)(using Envri): Boolean =
		getInstServerForDataObj(hash).map{
			_.hasStatement(
				vocab.getStaticObject(hash), RDF.TYPE, metaVocab.dataObjectClass
			)
		}.getOrElse(false)

	def isExistingDocument(hash: Sha256Sum)(using envri: Envri): Boolean =
		docServers.get(envri).map{
			_.hasStatement(
				vocab.getStaticObject(hash), RDF.TYPE, metaVocab.docObjectClass
			)
		}.getOrElse(false)

	private def getInstServerForDataObj(objHash: Sha256Sum)(using Envri): Try[InstanceServer] =
		for(
			objSpec <- getDataObjSpecification(objHash);
			format <- getObjSpecificationFormat(objSpec);
			server <- getInstServerForFormat(format)
		) yield server

	def getDocInstServer(using envri: Envri): Try[InstanceServer] = docServers.get(envri).toTry{
		new UploadUserErrorException(s"ENVRI '$envri' has no document instance server configured for it")
	}

	def getCollectionCreator(coll: Sha256Sum)(using Envri): Option[IRI] =
		collFetcherLite.flatMap(_.getCreatorIfCollExists(coll))

	def collectionExists(coll: Sha256Sum)(using Envri): Boolean =
		collFetcherLite.map(_.collectionExists(coll)).getOrElse(false)

	def collectionExists(coll: IRI)(using Envri): Boolean =
		collFetcherLite.map(_.collectionExists(coll)).getOrElse(false)

	def collFetcher(citer: CitationMaker)(using envri: Envri): Option[CollectionFetcher] = for(
		collServer <- collectionServers.get(envri);
		fetcher <- metaFetchers.get(envri)
	) yield new CollectionFetcher(collServer, fetcher.plainObjFetcher, citer)

	def collFetcherLite(using envri: Envri): Option[CollectionFetcherLite] = collectionServers.get(envri)
		.map(new CollectionFetcherLite(_, vocab))

	def dataObjExists(dobj: IRI)(using envri: Envri): Boolean =
		allDataObjs(envri).hasStatement(dobj, RDF.TYPE, metaVocab.dataObjectClass)

	def docObjExists(dobj: IRI)(using envri: Envri): Boolean =
		docServers(envri).hasStatement(dobj, RDF.TYPE, metaVocab.docObjectClass)

	def getObjSubmitter(obj: ObjectUploadDto)(using envri: Envri): Option[IRI] = {
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
