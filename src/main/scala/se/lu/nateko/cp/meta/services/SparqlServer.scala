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
import java.io.InputStream

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

		val source = InputStreamSource(() => {
			val conn = repo.getConnection
	
			val outStream = new java.io.PipedOutputStream
			val inStream = new java.io.PipedInputStream(outStream, InputStreamSource.DefaultChunkSize)
	
			val resultWriter = resType.writerFactory.getWriter(outStream)
			val tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query)
	
			val evaluation = Future(tupleQuery.evaluate(resultWriter))
			val crashable = new CrashableInputStream(inStream, evaluation)

			evaluation.onComplete(_ => {
				conn.close()
				outStream.flush()
				outStream.close()
				crashable.close()
			})
			crashable
		})

		HttpResponse(entity = HttpEntity(ContentType(resType.exactType, utf8), source))
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

private class CrashableInputStream(inner: InputStream, computation: Future[Unit]) extends InputStream{
	def read(): Int = {
		if(!computation.isCompleted || computation.value.get.isSuccess) inner.read()
		else throw computation.value.get.failed.get
	}

	override def read(b: Array[Byte]): Int = {
		if(!computation.isCompleted || computation.value.get.isSuccess) inner.read(b)
		else throw computation.value.get.failed.get
	}

	override def read(b: Array[Byte], off: Int, len: Int): Int = {
		if(!computation.isCompleted || computation.value.get.isSuccess) inner.read(b, off, len)
		else throw computation.value.get.failed.get
	}

	override def close() = inner.close()
	override def available() = inner.available()
	override def skip(n: Long) = inner.skip(n)
}
