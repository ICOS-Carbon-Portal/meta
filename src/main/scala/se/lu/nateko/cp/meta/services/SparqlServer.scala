package se.lu.nateko.cp.meta.services

import java.io.ByteArrayOutputStream

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.openrdf.query.QueryLanguage
import org.openrdf.query.resultio.TupleQueryResultWriterFactory
import org.openrdf.query.resultio.sparqljson.SPARQLResultsJSONWriterFactory
import org.openrdf.query.resultio.text.csv.SPARQLResultsCSVWriterFactory
import org.openrdf.query.resultio.text.tsv.SPARQLResultsTSVWriterFactory
import org.openrdf.repository.Repository

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.Marshalling
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.MediaTypes
import se.lu.nateko.cp.meta.utils.sesame._

case class SparqlSelect(query: String)

trait SparqlServer {
	/**
	 * Executes a SPARQL SELECT query
	 * Serializes the query results to one of the standard formats, depending on HTTP content negotiation
	 */
	def marshaller: ToResponseMarshaller[SparqlSelect]
}

private case class SparqlResultType(
	popularType: ContentType,
	contentType: ContentType,
	writerFactory: TupleQueryResultWriterFactory
)


class SesameSparqlServer(repo: Repository) extends SparqlServer{
	import SesameSparqlServer._

	private val resTypes: List[SparqlResultType] = List(
		SparqlResultType(
			popularType = ContentTypes.`application/json`,
			contentType = getSparqlContentType("application/sparql-results+json", ".srj"),
			writerFactory = new SPARQLResultsJSONWriterFactory()
		),
		SparqlResultType(
			popularType = ContentTypes.`text/csv(UTF-8)`,
			contentType = getSparqlContentType("text/csv", ".csv"),
			writerFactory = new SPARQLResultsCSVWriterFactory()
		),
		SparqlResultType(
			popularType = ContentTypes.`text/plain(UTF-8)`,
			contentType = getSparqlContentType("text/tab-separated-values", ".tsv"),
			writerFactory = new SPARQLResultsTSVWriterFactory()
		)
	)

	def marshaller: ToResponseMarshaller[SparqlSelect] = Marshaller(
		implicit exeCtxt => query => Future.successful(
			resTypes.map(resType => {
				Marshalling.WithFixedContentType(resType.popularType, () => getResponse(query.query, resType))
			})
		)
	)

	private def getResponse(query: String, resType: SparqlResultType)
			(implicit executor: ExecutionContext): HttpResponse = {

		val bytes = repo.accessEagerly(conn => {
			val outStream = new ByteArrayOutputStream
	
			val resultWriter = resType.writerFactory.getWriter(outStream)
			val tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query)
	
			tupleQuery.evaluate(resultWriter)
			outStream.close()
			outStream.toByteArray
		})

		HttpResponse(entity = HttpEntity(resType.contentType, bytes))
	}
}

object SesameSparqlServer{

	private val utf8 = HttpCharsets.`UTF-8`

	def getSparqlContentType(mimeType: String, fileExtension: String): ContentType = {
		val mediaType = MediaType.custom(mimeType, false, fileExtensions = List(fileExtension))
		ContentType(mediaType, () => utf8)
	}

}

