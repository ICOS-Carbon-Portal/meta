package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import se.lu.nateko.cp.meta.rdfstore.DerivedMetadataJsonProtocol.given
import spray.json.*

import java.net.URI
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

/** The standalone RDF store, reached over HTTP */
final class RemoteRdfStore(config: RdfStoreConfig)(using system: ActorSystem) extends AutoCloseable:
	import RemoteRdfStore.*
	private given ExecutionContext = system.dispatcher

	/** The repository with the SPARQL magic index and derived metadata */
	val repo: Repository = sparqlRepository(config.sparqlEndpoint)

	/** The repository with only the stored statements; read-only */
	val vanillaRepo: Repository = sparqlRepository(config.vanillaSparqlEndpoint)

	val derivedMetadata: DerivedMetadataSource = new DerivedMetadataSource:

		def resolve(resources: Seq[URI]): Future[DerivedMetadataResponse] =
			val payload = DerivedMetadataRequest(resources).toJson.compactPrint
			post(config.derivedMetadataEndpoint, HttpEntity(ContentTypes.`application/json`, payload))
				.map(_.parseJson.convertTo[DerivedMetadataResponse])

		def dropDoiCache(doi: String): Future[Unit] =
			post(config.dropDoiCacheEndpoint(doi), HttpEntity.Empty).map(_ => ())

	/** Switches the RDF store to read-only mode, dumping the SPARQL magic index and citation caches to disk */
	def makeReadonlyDumpIndexAndCaches(message: String): Future[String] =
		post(config.adminEndpoint, HttpEntity(ContentTypes.`text/plain(UTF-8)`, message))

	private def post(endpoint: URI, entity: HttpEntity.Strict): Future[String] =
		Http().singleRequest(HttpRequest(HttpMethods.POST, endpoint.toString, entity = entity)).flatMap(bodyIfOk(endpoint))

	override def close(): Unit =
		repo.shutDown()
		vanillaRepo.shutDown()

end RemoteRdfStore

object RemoteRdfStore:

	/** Makes IRIs from namespace and local name the way the LMDB and native stores do, by joining them, so that
	  * getLocalName (and hence UriId) of e.g. vocab.getStation is the same as with the store in the same process
	  */
	object StoreLikeValueFactory extends SimpleValueFactory:
		override def createIRI(namespace: String, localName: String): IRI = createIRI(namespace + localName)

	private def sparqlRepository(endpoint: URI): Repository =
		val repo = new SPARQLRepository(endpoint.toString, endpoint.toString):
			override def getValueFactory: ValueFactory = StoreLikeValueFactory
		repo.enableQuadMode(true)
		repo.init()
		repo

	private def bodyIfOk(endpoint: URI)(response: HttpResponse)(using system: ActorSystem): Future[String] =
		import system.dispatcher
		response.entity.toStrict(30.seconds).flatMap: entity =>
			val body = entity.data.utf8String
			if response.status.isSuccess then Future.successful(body)
			else Future.failed(RuntimeException(s"RDF store request to $endpoint failed with ${response.status}: $body"))

end RemoteRdfStore
