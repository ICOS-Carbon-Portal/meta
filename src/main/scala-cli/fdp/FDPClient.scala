import sttp.client4.quick.*
import sttp.client4.upicklejson.default.*
import sttp.model.Uri
import sttp.client4.Response
import upickle.default.*
import org.eclipse.rdf4j.model.{IRI,Resource,Value,Statement}
import org.eclipse.rdf4j.model.vocabulary.{RDF,DCAT,LDP}
import org.eclipse.rdf4j.rio.Rio
import org.eclipse.rdf4j.rio.RDFFormat
import java.io.StringReader
import scala.jdk.CollectionConverters.IterableHasAsScala

final case class User(email: String, password: String) derives ReadWriter
final case class Token(token: String) derives ReadWriter
//final case class QueryRequest(graphPattern: String, ordering: String, prefixes: String) derives ReadWriter

// class FDPPublicClient(val host: Uri):
// 	def query(req: QueryRequest): Seq[SearchResult]
class FDPClient private(val host: Uri, token: String): //extends FDPPublicClient(host)
	import FDPClient.*

	def authRequest = quickRequest.auth.bearer(token)

	def parseTurtleAndGetStatements(ttl: String, subj: Resource, pred: IRI, obj: Value): Iterable[Statement] =
		Rio.parse(StringReader(ttl), "", RDFFormat.TURTLE).getStatements(subj, pred, obj).asScala

	def catalogUriInFdp(catalog: String): Uri =
		val fdpTtl = quickRequest.get(host).send().body
		val statements = parseTurtleAndGetStatements(fdpTtl, null, LDP.CONTAINS, null)
		val catalogUris: IndexedSeq[Uri] = statements
			.filter: st =>
				val objectUri = uri"${st.getObject().toString()}"
				objectUri.path(0) == "catalog" && objectUri.path(1) == catalog
			.map: st =>
				uri"${st.getObject().toString()}"
			.toIndexedSeq
		catalogUris.length match
			case 1 => catalogUris(0)
			case _ =>
				println(s"Warning: zero or more than one URI found for catalog '$catalog'. Using the first one by default (${catalogUris(0)}).")
				catalogUris(0)

	def postDataset(ttl: String): IndexedSeq[Uri] =
		postResource(ttl, "dataset", DCAT.DATASET)

	def postDistribution(ttl: String): IndexedSeq[Uri] =
		postResource(ttl, "distribution", DCAT.DISTRIBUTION)

	def postResource(ttl: String, category: String, dcatClass: IRI): IndexedSeq[Uri] =
		val uri = host.addPath(category)
		val resp = authRequest.post(uri).headers(turtleTurtle).body(ttl).send()
		warnIfHttpError(resp, s"Failed to POST Turtle:\n$ttl\n")
		val statements = parseTurtleAndGetStatements(resp.body, null, RDF.TYPE, dcatClass)
		statements
			.map: st =>
				val uri = st.getSubject().toString()
				uri"$uri"
			.toIndexedSeq

	def publishResource(resource: Uri): Response[String] =
		val uri = resource.addPath("meta", "state")
		val status = ujson.Obj("current" -> ujson.Str("PUBLISHED"))
		val resp = authRequest.put(uri).headers(jsonJson).body(status).send()
		warnIfHttpError(resp, "Failed to publish resource.")
		resp

	def postAndPublishDatasets(ttl: String): IndexedSeq[Uri] =
		postAndPublishResources(ttl, postDataset)

	def postAndPublishDistributions(ttl: String): IndexedSeq[Uri] =
		postAndPublishResources(ttl, postDistribution)

	def postAndPublishResources(ttl: String, postFunction: String => IndexedSeq[Uri]): IndexedSeq[Uri] =
		val datasetUris = postFunction(ttl)
		for datasetUri <- datasetUris do
			val resp = publishResource(uri"$host/${datasetUri.path}")
		datasetUris

	def deleteDataset(dataset: Uri): Response[String] =
		val resp = authRequest.delete(dataset).send()
		warnIfHttpError(resp, "Failed to delete resource.")
		resp
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

	def warnIfHttpError(resp: Response[String], msg: String): Unit =
		if (!(resp.code.isSuccess)) println(s"\n\n$msg\nResponse:\n${resp.body}")