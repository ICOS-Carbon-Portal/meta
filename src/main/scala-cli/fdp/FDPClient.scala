import scala.collection.mutable.ArrayBuffer
import sttp.client4.quick.*
import sttp.model.Uri
import sttp.client4.Response
import upickle.default.*
import org.eclipse.rdf4j.model.vocabulary.{RDF,DCAT}
import org.eclipse.rdf4j.rio.Rio
import org.eclipse.rdf4j.rio.RDFFormat
import java.io.StringReader

case class User(email: String, password: String) derives ReadWriter
case class Token(token: String) derives ReadWriter

class FDPClient(hostUri: Uri):

	private val jsonJson = Map("Content-Type" -> "application/json", "Accept" -> "application/json")
	private val turtleTurtle = Map("Content-Type" -> "text/turtle", "Accept" -> "text/turtle")

	val host = hostUri
	print("Enter your email: ")
	private val email = System.console().readLine()
	print("Enter your password: ")
	private val password = System.console().readPassword()
	private val token = getToken(User(email.mkString, password.mkString))

	def getToken(user: User): String =
		val uri = host.addPath("tokens")
		val body = upickle.default.write(user)
		val resp: Response[String] = quickRequest.post(uri).headers(jsonJson).body(body).send()
		upickle.default.read[Token](resp.body).token

	def postDataset(ttl: String): IndexedSeq[Uri] =
		val uri = host.addPath("dataset")
		val resp = quickRequest.post(uri).headers(turtleTurtle).auth.bearer(token).body(ttl).send()
		val model = Rio.parse(StringReader(resp.body), "", RDFFormat.TURTLE)
		val datasetsUri = ArrayBuffer[Uri]()
		model.getStatements(null, RDF.TYPE, DCAT.DATASET).forEach: st =>
			val uri = st.getSubject().toString()
			datasetsUri.append(uri"$uri")
		datasetsUri.toIndexedSeq

	def publishDataset(dataset: Uri): Response[String] =
		val uri = dataset.addPath("meta", "state")
		val body = "{\"current\": \"PUBLISHED\"}"
		quickRequest.put(uri).headers(jsonJson).auth.bearer(token).body(body).send()

	def postAndPublishDatasets(ttl: String): Unit =
		val datasetUris = postDataset(ttl)
		for datasetUri <- datasetUris do
			publishDataset(uri"$datasetUri")

	def deleteDataset(dataset: Uri): Response[String] =
		quickRequest.delete(dataset).auth.bearer(token).send()

object FDPClient:
	def apply(hostUri: Uri) =
		new FDPClient(hostUri)