import sttp.client4.quick.*
import sttp.client4.upicklejson.default.*
import sttp.client4.{basicRequest,Response,BasicBody,StringBody}
import sttp.model.{Uri,MediaType}
import upickle.default.*
import org.eclipse.rdf4j.model.{IRI,Resource,Value,Statement}
import org.eclipse.rdf4j.model.vocabulary.{RDF,DCAT,LDP}
import org.eclipse.rdf4j.rio.Rio
import org.eclipse.rdf4j.rio.RDFFormat
import java.io.StringReader
import scala.jdk.CollectionConverters.IterableHasAsScala

case class User(email: String, password: String) derives ReadWriter
case class Token(token: String) derives ReadWriter

class ShaclException(message: String) extends Exception(message)

class FDPClient private(val host: Uri, token: String):
	import FDPClient.*

	def authRequest = basicRequest.auth.bearer(token)

	def parseTurtleAndGetStatements(ttl: String, subj: Resource, pred: IRI, obj: Value): Iterable[Statement] =
		Rio.parse(StringReader(ttl), "", RDFFormat.TURTLE).getStatements(subj, pred, obj).asScala

	def processHttpResponse(resp: Response[Either[String, String]], hint: String): String =
		resp.body match {
			case Left(errorMsg) =>
				if errorMsg.contains("ValidationReport") then
					throw new ShaclException(s"$hint\n\nSHACL validation report:\n$errorMsg\n\n")
				else
					throw new Exception(s"$hint\n\n$errorMsg")
			case Right(ttl) =>
				ttl
		}

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
		val respTtl = processHttpResponse(resp, s"Failed to POST Turtle:\n$ttl\n")
		val statements = parseTurtleAndGetStatements(respTtl, null, RDF.TYPE, dcatClass)
		statements
			.map: st =>
				val uri = st.getSubject().toString()
				uri"$uri"
			.toIndexedSeq

	def publishResource(resource: Uri): Unit =
		val uri = resource.addPath("meta", "state")
		val status = "{ \"current\": \"PUBLISHED\" }"
		val resp = authRequest.put(uri).headers(jsonJson).body(status).send()
		val respMsg = processHttpResponse(resp, s"Failed to publish resource $resource.")

	def postAndPublishDatasets(ttl: String): IndexedSeq[Uri] =
		postAndPublishResources(ttl, postDataset)

	def postAndPublishDistributions(ttl: String): IndexedSeq[Uri] =
		postAndPublishResources(ttl, postDistribution)

	def postAndPublishResources(ttl: String, postFunction: String => IndexedSeq[Uri]): IndexedSeq[Uri] =
		val datasetUris = postFunction(ttl)
		for datasetUri <- datasetUris do
			val resp = publishResource(uri"$host/${datasetUri.path}")
		datasetUris

	def deleteDataset(dataset: Uri): Unit =
		val resp = authRequest.delete(dataset).send()
		val respMsg = processHttpResponse(resp, s"Failed to delete resource $dataset.")

end FDPClient

object FDPClient:
	private val jsonJson = Map("Content-Type" -> "application/json", "Accept" -> "application/json")
	private val turtleTurtle = Map("Content-Type" -> "text/turtle", "Accept" -> "text/turtle")

	def serializeUser(user: User): BasicBody =
		val serializedUser = s"{\"email\": \"${user.email}\", \"password\": \"${user.password}\"}"
		StringBody(serializedUser, "UTF-8", MediaType.ApplicationJson)

	def apply(host: Uri, user: User): FDPClient =
		val uri = host.addPath("tokens")
		basicRequest
			.post(uri)
			.headers(jsonJson)
			.body(serializeUser(user))
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