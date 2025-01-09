import scala.collection.mutable.ArrayBuffer
import sttp.client4.quick.*
import sttp.client4.upicklejson.default.*
import sttp.model.Uri
import sttp.client4.Response
import upickle.default.*
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.{RDF,DCAT}
import org.eclipse.rdf4j.rio.Rio
import org.eclipse.rdf4j.rio.RDFFormat
import java.io.StringReader
import scala.jdk.CollectionConverters.IterableHasAsScala

case class User(email: String, password: String) derives ReadWriter
case class Token(token: String) derives ReadWriter
//case class QueryRequest(graphPattern: String, ordering: String, prefixes: String) derives ReadWriter

// class FDPPublicClient(val host: Uri):
// 	def query(req: QueryRequest): Seq[SearchResult]
class FDPClient private(val host: Uri, token: String): //extends FDPPublicClient(host)
	import FDPClient.*

	def authRequest = quickRequest.auth.bearer(token)

	def postDataset(ttl: String): IndexedSeq[Uri] =
		postResource(ttl, "dataset", DCAT.DATASET)

	def postDistribution(ttl: String): IndexedSeq[Uri] =
		postResource(ttl, "distribution", DCAT.DISTRIBUTION)

	def postResource(ttl: String, category: String, dcatClass: IRI): IndexedSeq[Uri] =
		val uri = host.addPath(category)
		val resp = authRequest.post(uri).headers(turtleTurtle).body(ttl).send()
		Rio
			.parse(StringReader(resp.body), "", RDFFormat.TURTLE)
			.getStatements(null, RDF.TYPE, dcatClass)
			.asScala
			.map: st =>
				val uri = st.getSubject().toString()
				uri"$uri"
			.toIndexedSeq

	def publishResource(resource: Uri): Response[String] =
		val uri = resource.addPath("meta", "state")
		val status = ujson.Obj("current" -> ujson.Str("PUBLISHED"))
		authRequest.put(uri).headers(jsonJson).body(status).send()

	def postAndPublishDatasets(ttl: String): Unit =
		postAndPublishResources(ttl, postDataset)

	def postAndPublishDistributions(ttl: String): Unit =
		postAndPublishResources(ttl, postDistribution)

	def postAndPublishResources(ttl: String, postFunction: String => IndexedSeq[Uri]): Unit =
		val datasetUris = postFunction(ttl)
		for datasetUri <- datasetUris do
			publishResource(uri"$datasetUri")

	def deleteDataset(dataset: Uri): Response[String] =
		authRequest.delete(dataset).send()
end FDPClient

object FDPClient:
	private val jsonJson = Map("Content-Type" -> "application/json", "Accept" -> "application/json")
	private val turtleTurtle = Map("Content-Type" -> "text/turtle", "Accept" -> "text/turtle")

	def apply(host: Uri, user: User): FDPClient =
		val uri = host.addPath("tokens")
		quickRequest
			.post(uri)//.headers(jsonJson)
			.body(user)
			.response(asJson[Token])
			.send().body match
			case Left(exc) => throw exc
			case Right(token) => new FDPClient(host, token.token)

	def interactiveInit(host: Uri): FDPClient =
		print("Enter your email: ")
		val email = System.console().readLine()
		print("Enter your password: ")
		val password = System.console().readPassword()
		val user = User(email.mkString, password.mkString)
		apply(host, user)
