package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import org.eclipse.rdf4j.model.{Literal, Statement}
import org.eclipse.rdf4j.model.impl.{LinkedHashModel, SimpleValueFactory}
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}
import se.lu.nateko.cp.meta.QleverConfig

import java.io.StringWriter
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

class QleverClient(val config: QleverConfig)(using system: ActorSystem, mat: Materializer):
	private val http = Http()
	private given ExecutionContext = system.dispatcher
	private val endpoint = Uri(config.endpoint)
	private val vf = SimpleValueFactory.getInstance()
	private val XsdBoolean = "http://www.w3.org/2001/XMLSchema#boolean"
	private val NTriples = MediaType.applicationWithFixedCharset("n-triples", HttpCharsets.`UTF-8`)
	private val TSOP = HttpMethod.custom("TSOP")

	def sparqlQuery(query: String, acceptMime: String): Future[HttpResponse] =
		val request = HttpRequest(
			method = HttpMethods.POST,
			uri = endpoint,
			headers = List(RawHeader("Accept", acceptMime)),
			entity = FormData("query" -> query).toEntity
		)
		http.singleRequest(request)

	def sparqlUpdate(update: String): Future[Done] =
		val formFields = config.accessToken match
			case Some(token) => Map("update" -> update, "access-token" -> token)
			case None => Map("update" -> update)
		val request = HttpRequest(
			method = HttpMethods.POST,
			uri = endpoint,
			entity = FormData(formFields).toEntity
		)
		http.singleRequest(request).flatMap: resp =>
			if resp.status.isSuccess then
				resp.entity.discardBytes().future()
			else
				resp.entity.toStrict(5.seconds).flatMap: strict =>
					Future.failed(Exception(s"QLever update failed with ${resp.status}: ${strict.data.utf8String}"))

	def graphStoreAdd(graphUri: String, statements: Iterable[Statement]): Future[Done] =
		graphStoreRequest(HttpMethods.POST, graphUri, statementsToNTriples(statements))

	def graphStoreRemove(graphUri: String, statements: Iterable[Statement]): Future[Done] =
		graphStoreRequest(TSOP, graphUri, statementsToNTriples(statements))

	def graphStoreClear(graphUri: String): Future[Done] =
		graphStoreRequest(HttpMethods.DELETE, graphUri, "")

	private def graphStoreRequest(method: HttpMethod, graphUri: String, body: String): Future[Done] =
		val uri = endpoint.withQuery(Uri.Query("graph" -> graphUri))
		val authHeaders = config.accessToken.map(t => RawHeader("Authorization", s"Bearer $t")).toList
		val entity = if body.nonEmpty then HttpEntity(ContentType(NTriples), body) else HttpEntity.Empty
		val request = HttpRequest(method = method, uri = uri, headers = authHeaders, entity = entity)
		http.singleRequest(request).flatMap: resp =>
			if resp.status.isSuccess then
				resp.entity.discardBytes().future()
			else
				resp.entity.toStrict(5.seconds).flatMap: strict =>
					Future.failed(Exception(s"QLever graph store request failed with ${resp.status}: ${strict.data.utf8String}"))

	private def statementsToNTriples(statements: Iterable[Statement]): String =
		val model = new LinkedHashModel()
		statements.foreach: stmt =>
			model.add(normalizeStatement(stmt))
		val sw = new StringWriter()
		Rio.write(model, sw, RDFFormat.NTRIPLES)
		sw.toString

	private def normalizeStatement(stmt: Statement): Statement =
		stmt.getObject match
			case lit: Literal if lit.getDatatype != null && lit.getDatatype.stringValue == XsdBoolean =>
				val normalized = vf.createLiteral(lit.getLabel.toLowerCase, lit.getDatatype)
				vf.createStatement(stmt.getSubject, stmt.getPredicate, normalized)
			case _ => stmt

end QleverClient
