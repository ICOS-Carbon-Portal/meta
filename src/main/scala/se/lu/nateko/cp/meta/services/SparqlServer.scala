package se.lu.nateko.cp.meta.services

import org.openrdf.repository.Repository
import se.lu.nateko.cp.meta.utils.sesame._
import java.io.ByteArrayOutputStream
import org.openrdf.query.resultio.sparqljson.SPARQLResultsJSONWriterFactory
import org.openrdf.query.QueryLanguage
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.openrdf.query.resultio.TupleQueryResultWriterFactory
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.stream.io.InputStreamSource
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.marshalling.Marshalling
import akka.http.scaladsl.marshalling.Marshalling.WithFixedCharset
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.HttpResponse

case class SparqlSelect(query: String)

trait SparqlServer {
	/**
	 * Executes a SPARQL SELECT query
	 * @return Query results in SPARQL-JSON format
	 */
	def marshaller: ToResponseMarshaller[SparqlSelect]
}

private case class SparqlResultType(popularType: MediaType, exactType: MediaType, writerFactory: TupleQueryResultWriterFactory)


class SesameSparqlServer(repo: Repository) extends SparqlServer{
	import SparqlServer._

	def executeQuery(query: String): String = repo.accessEagerly{ conn =>
		val stream = new ByteArrayOutputStream
		val resultWriter = new SPARQLResultsJSONWriterFactory().getWriter(stream)
		val tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query)
		tupleQuery.evaluate(resultWriter)
		stream.toString("UTF-8")
	}

	private val utf8 = HttpCharsets.`UTF-8`

	private val resTypes: List[SparqlResultType] = List(
		SparqlResultType(
			popularType = MediaTypes.`application/json`,
			exactType = getSparqlResMediaType("json", ".srj"),
			writerFactory = new SPARQLResultsJSONWriterFactory()
		)
	)

	def marshaller: ToResponseMarshaller[SparqlSelect] = Marshaller(implicit exeCtxt => query => Future{
		resTypes.map(resType => WithFixedCharset(resType.popularType, utf8, () => getEntity(query.query, resType)))
	})

	private def getEntity(query: String, resType: SparqlResultType)
			(implicit executor: ExecutionContext): HttpResponse = {

		val conn = repo.getConnection

		val outStream = new java.io.PipedOutputStream
		val inStream = new java.io.PipedInputStream(outStream)

		val resultWriter = resType.writerFactory.getWriter(outStream)
		val tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query)

		Future{
			tupleQuery.evaluate(resultWriter)
		}.onComplete(_ => {
			conn.close()
			outStream.close()
		})

		val source = InputStreamSource(() => inStream)
		val contType = ContentType(resType.exactType, utf8)
		val entity = HttpEntity(contType, source)
		HttpResponse(entity = entity)
	}
}

object SparqlServer{
	
	def getSparqlResMediaType(subtype: String, fileExtension: String): MediaType = MediaType.custom(
		mainType = "application",
		subType = "sparql-results+" + subtype,
		encoding = MediaType.Encoding.Fixed(HttpCharsets.`UTF-8`),
		compressible = true,
		fileExtensions = fileExtension :: Nil
	)

}
