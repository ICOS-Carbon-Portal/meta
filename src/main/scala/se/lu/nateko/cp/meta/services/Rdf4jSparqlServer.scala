package se.lu.nateko.cp.meta.services

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.eclipse.rdf4j.query.BooleanQuery
import org.eclipse.rdf4j.query.GraphQuery
import org.eclipse.rdf4j.query.MalformedQueryException
import org.eclipse.rdf4j.query.TupleQuery
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterFactory
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriterFactory
import org.eclipse.rdf4j.query.resultio.sparqlxml.SPARQLResultsXMLWriterFactory
import org.eclipse.rdf4j.query.resultio.text.csv.SPARQLResultsCSVWriterFactory
import org.eclipse.rdf4j.query.resultio.text.tsv.SPARQLResultsTSVWriterFactory
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.rio.RDFWriterFactory
import org.eclipse.rdf4j.rio.rdfxml.RDFXMLWriterFactory
import org.eclipse.rdf4j.rio.turtle.TurtleWriterFactory

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
import akka.http.scaladsl.model.StatusCodes
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.api.SparqlServer
import se.lu.nateko.cp.meta.services.linkeddata.InstanceServerSerializer
import se.lu.nateko.cp.meta.utils.streams.OutputStreamWriterSource

private case class SparqlNegotiationOption(
	requestedType: ContentType,
	returnedType: ContentType,
	writerFactory: TupleQueryResultWriterFactory
)


class Rdf4jSparqlServer(repo: Repository, config: SparqlServerConfig) extends SparqlServer{
	import Rdf4jSparqlServer._

	private val jsonType = getSparqlContentType("application/sparql-results+json", ".srj")
	private val xmlSparql = getSparqlContentType("application/sparql-results+xml", ".srx")
	private val csvSparql = getSparqlContentType("text/csv", ".csv")
	private val tsvSparql = getSparqlContentType("text/tab-separated-values", ".tsv")
	private def jsonWriterFactory = new SPARQLResultsJSONWriterFactory()
	private def xmlWriterFactory = new SPARQLResultsXMLWriterFactory()

	private val xml = ContentType(MediaTypes.`application/xml`, utf8)

	private val javaExe = Executors.newCachedThreadPool()
	private val canceller = Executors.newSingleThreadScheduledExecutor()

	implicit private val ctxt = ExecutionContext.fromExecutorService(javaExe)

	def marshaller: ToResponseMarshaller[SparqlQuery] = Marshaller(
		exeCtxt => query => Future{
			val conn = repo.getConnection
			try{
				val onDone: () => Unit = () => conn.close()

				conn.prepareQuery(query.query) match {
					case tupleQuery: TupleQuery =>
						Marshalling.WithFixedContentType(
							jsonType,
							() => getTupleQueryResponse(tupleQuery, jsonWriterFactory, jsonType, onDone)
						) ::
						Marshalling.WithFixedContentType(
							ContentTypes.`application/json`,
							() => getTupleQueryResponse(tupleQuery, jsonWriterFactory, jsonType, onDone)
						) ::
						Marshalling.WithFixedContentType(
							xmlSparql,
							() => getTupleQueryResponse(tupleQuery, xmlWriterFactory, xmlSparql, onDone)
						) ::
						Marshalling.WithFixedContentType(
							xml,
							() => getTupleQueryResponse(tupleQuery, xmlWriterFactory, xmlSparql, onDone)
						) ::
						Marshalling.WithFixedContentType(
							ContentTypes.`text/csv(UTF-8)`,
							() => getTupleQueryResponse(tupleQuery, new SPARQLResultsCSVWriterFactory(), csvSparql, onDone)
						) ::
						Marshalling.WithFixedContentType(
							ContentTypes.`text/plain(UTF-8)`,
							() => getTupleQueryResponse(tupleQuery, new SPARQLResultsTSVWriterFactory(), tsvSparql, onDone)
						) :: Nil
					case graphQuery: GraphQuery =>
						Marshalling.WithFixedContentType(
							ContentTypes.`text/plain(UTF-8)`,
							() => getGraphQueryResponse(graphQuery, new TurtleWriterFactory(), InstanceServerSerializer.turtleContType, onDone)
						) ::
						Marshalling.WithFixedContentType(
							xml,
							() => getGraphQueryResponse(graphQuery, new RDFXMLWriterFactory(), InstanceServerSerializer.xmlContType, onDone)
						) ::
						Marshalling.WithFixedContentType(
							InstanceServerSerializer.xmlContType,
							() => getGraphQueryResponse(graphQuery, new RDFXMLWriterFactory(), InstanceServerSerializer.xmlContType, onDone)
						) :: Nil
					case _: BooleanQuery =>
						Marshalling.Opaque(() => HttpResponse(
							status = StatusCodes.NotImplemented,
							entity = "Boolean queries are not supported yet"
						)) :: Nil
				}
			} catch {
				case userErr: MalformedQueryException =>
					conn.close()
					Marshalling.Opaque(
						() => HttpResponse(status = StatusCodes.BadRequest, entity = userErr.getMessage)
					) :: Nil
				case err: Throwable =>
					conn.close()
					throw err
			}
		}(exeCtxt)
	)

	private def getTupleQueryResponse(
		query: TupleQuery,
		writerFactory: TupleQueryResultWriterFactory,
		returnedType: ContentType,
		onDone: () => Unit
	): HttpResponse = {

		val entityBytes = OutputStreamWriterSource{ outStr =>
			runWithTimeout{
				val resultWriter = writerFactory.getWriter(outStr)
				query.evaluate(resultWriter)
			}{
				outStr.close()
				onDone()
			}
		}

		HttpResponse(entity = HttpEntity(returnedType, entityBytes))
	}

	private def getGraphQueryResponse(
		query: GraphQuery,
		writerFactory: RDFWriterFactory,
		returnedType: ContentType,
		onDone: () => Unit
	): HttpResponse = {

		val entityBytes = OutputStreamWriterSource{ outStr =>
			runWithTimeout{
				val resultWriter = writerFactory.getWriter(outStr)
				query.evaluate(resultWriter)
			}{
				outStr.close()
				onDone()
			}
		}

		HttpResponse(entity = HttpEntity(returnedType, entityBytes))
	}

	private def runWithTimeout(code: => Unit)(finish: => Unit): Unit = {
		val task: Callable[Unit] = () => code
		val fut = javaExe.submit(task)

		val cancellation: Runnable = () => if(!fut.isDone) fut.cancel(true)

		canceller.schedule(cancellation, config.maxQueryRuntimeSec.toLong, TimeUnit.SECONDS)

		try{
			fut.get
		} finally{
			finish
		}
	}
}

object Rdf4jSparqlServer{

	private val utf8 = HttpCharsets.`UTF-8`

	def getSparqlContentType(mimeType: String, fileExtension: String): ContentType = {
		val mediaType = MediaType.custom(mimeType, false, fileExtensions = List(fileExtension))
		ContentType(mediaType, () => utf8)
	}

}
