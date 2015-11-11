package se.lu.nateko.cp.meta.services

import java.io.ByteArrayOutputStream
import java.io.InputStream

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.openrdf.query.QueryLanguage
import org.openrdf.query.resultio.TupleQueryResultWriterFactory
import org.openrdf.query.resultio.sparqljson.SPARQLResultsJSONWriterFactory
import org.openrdf.query.resultio.text.csv.SPARQLResultsCSVWriterFactory
import org.openrdf.query.resultio.text.tsv.SPARQLResultsTSVWriterFactory
import org.openrdf.repository.Repository

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.Marshalling.WithFixedCharset
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.MediaTypes
import se.lu.nateko.cp.meta.utils.sesame.SesameRepoWithAccessAndTransactions

case class SparqlSelect(query: String)

trait SparqlServer {
	/**
	 * Executes a SPARQL SELECT query
	 * Serializes the query results to one of the standard formats, depending on HTTP content negotiation
	 */
	def marshaller: ToResponseMarshaller[SparqlSelect]
}

private case class SparqlResultType(popularType: MediaType, exactType: MediaType, writerFactory: TupleQueryResultWriterFactory)


class SesameSparqlServer(repo: Repository) extends SparqlServer{
	import SparqlServer._

	private val utf8 = HttpCharsets.`UTF-8`

	private val resTypes: List[SparqlResultType] = List(
		SparqlResultType(
			popularType = MediaTypes.`application/json`,
			exactType = getSparqlResMediaType("application", "sparql-results+json", ".srj"),
			writerFactory = new SPARQLResultsJSONWriterFactory()
		),
		SparqlResultType(
			popularType = MediaTypes.`text/csv`,
			exactType = getSparqlResMediaType("text", "csv", ".csv"),
			writerFactory = new SPARQLResultsCSVWriterFactory()
		),
		SparqlResultType(
			popularType = MediaTypes.`text/plain`,
			exactType = getSparqlResMediaType("text", "tab-separated-values", ".tsv"),
			writerFactory = new SPARQLResultsTSVWriterFactory()
		)
	)

	def marshaller: ToResponseMarshaller[SparqlSelect] = Marshaller(
		implicit exeCtxt => query => Future.successful(
			resTypes.map(resType =>
				WithFixedCharset(resType.popularType, utf8, () => getResponse(query.query, resType))
			)
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

		HttpResponse(entity = HttpEntity(ContentType(resType.exactType, utf8), bytes))
	}
}

object SparqlServer{
	
	def getSparqlResMediaType(mainType: String, subtype: String, fileExtension: String): MediaType = MediaType.custom(
		mainType = mainType,
		subType = subtype,
		encoding = MediaType.Encoding.Fixed(HttpCharsets.`UTF-8`),
		compressible = true,
		fileExtensions = fileExtension :: Nil
	)

}

