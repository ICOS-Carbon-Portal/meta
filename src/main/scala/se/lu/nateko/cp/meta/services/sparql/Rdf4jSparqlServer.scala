package se.lu.nateko.cp.meta.services.sparql

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.eclipse.rdf4j.query.BooleanQuery
import org.eclipse.rdf4j.query.GraphQuery
import org.eclipse.rdf4j.query.MalformedQueryException
import org.eclipse.rdf4j.query.Query
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
import akka.http.scaladsl.model.StatusCode
import java.time.Instant


class Rdf4jSparqlServer(repo: Repository, config: SparqlServerConfig) extends SparqlServer{
	import Rdf4jSparqlServer._

	private val javaExe = Executors.newCachedThreadPool()
	private val canceller = Executors.newSingleThreadScheduledExecutor()
	private val quoter = new QuotaManager(config)(Instant.now _)

	implicit private val ctxt = ExecutionContext.fromExecutorService(javaExe)

	//QuotaManager should be cleaned periodically to forget very old query runs
	canceller.scheduleWithFixedDelay(() => quoter.cleanup(), 5, 5, TimeUnit.HOURS)

	def shutdown(): Unit = {
		javaExe.shutdown()
		canceller.shutdown()
	}

	def marshaller: ToResponseMarshaller[SparqlQuery] = Marshaller(
		exeCtxt => query => Future{
			val quotaExceeded = query.clientId.fold(false)(quoter.quotaExceeded)

			if(quotaExceeded) plainResponse(
				StatusCodes.ServiceUnavailable,
				"You have exceeded your SPARQL endpoint usage quota. Retry in 1 minute or in 1 hour."
			) else
				getSparqlingMarshallings(query)
		}(exeCtxt) //using Akka-provided ExecutionContext to handle the "outer shell" part of response marshalling
		           //(that is, everything except the actual SPARQL query evaluation, which is done by javaExe thread pool)
	)

	private def getSparqlingMarshallings(query: SparqlQuery): List[Marshalling[HttpResponse]] = {
		val conn = repo.getConnection
		val idOpt = query.clientId.map(quoter.logNewQueryStart)

		val onDone = () => {
			conn.close()
			for(qid <- idOpt; cid <- query.clientId) quoter.logQueryFinish(cid, qid)
		}

		try{
			conn.prepareQuery(query.query) match {
				case tupleQuery: TupleQuery =>

					tupleQueryProtocolOptions.map(po =>
						getQueryMarshalling(tupleQuery, po, onDone)
					)

				case graphQuery: GraphQuery =>

					graphQueryProtocolOptions.map(po =>
						getQueryMarshalling(graphQuery, po, onDone)
					)

				case _: BooleanQuery =>
					plainResponse(StatusCodes.NotImplemented, "Boolean queries are not supported yet")
			}
		} catch {
			case userErr: MalformedQueryException =>
				onDone()
				plainResponse(StatusCodes.BadRequest, userErr.getMessage)
			case err: Throwable =>
				onDone()
				throw err
		}
	}

	private def getQueryMarshalling[Q <: Query](
		query: Q,
		protocolOption: ProtocolOption[Q],
		onDone: () => Unit
	): Marshalling[HttpResponse] = Marshalling.WithFixedContentType(
		protocolOption.requestedResponseType,
		() => {
			val entityBytes = OutputStreamWriterSource{ outStr =>

				val fut = javaExe.submit[Unit](
					() => protocolOption.evaluator.evaluate(query, outStr)
				)

				canceller.schedule(
					() => if(!fut.isDone) fut.cancel(true),
					config.maxQueryRuntimeSec.toLong,
					TimeUnit.SECONDS
				)
				try{ fut.get } finally{
					outStr.close()
					onDone()
				}
			}

			HttpResponse(entity = HttpEntity(protocolOption.responseType, entityBytes))
		}
	)
}

object Rdf4jSparqlServer{

	private val utf8 = HttpCharsets.`UTF-8`
	private val xml = ContentType(MediaTypes.`application/xml`, utf8)

	private val jsonSparql = getSparqlContentType("application/sparql-results+json", ".srj")
	private val xmlSparql = getSparqlContentType("application/sparql-results+xml", ".srx")
	private val csvSparql = getSparqlContentType("text/csv", ".csv")
	private val tsvSparql = getSparqlContentType("text/tab-separated-values", ".tsv")

	def getSparqlContentType(mimeType: String, fileExtension: String): ContentType = {
		val mediaType = MediaType.custom(mimeType, false, fileExtensions = List(fileExtension))
		ContentType(mediaType, () => utf8)
	}

	private val jsonSparqlWriterFactory = new SPARQLResultsJSONWriterFactory()
	private val xmlSparqlWriterFactory = new SPARQLResultsXMLWriterFactory()
	private val csvSparqlWriterFactory = new SPARQLResultsCSVWriterFactory()
	private val tsvSparqlWriterFactory = new SPARQLResultsTSVWriterFactory()
	private val xmlRdfWriterFactory = new RDFXMLWriterFactory()
	private val turtleRdfWriterFactory = new TurtleWriterFactory()

	class ProtocolOption[Q <: Query](
		val responseType: ContentType,
		val requestedResponseType: ContentType,
		val evaluator: QueryEvaluator[Q]
	)

	object ProtocolOption{
		def apply(rt: ContentType, rrt: ContentType, wf: TupleQueryResultWriterFactory) =
			new ProtocolOption(rt, rrt, new TupleQueryEvaluator(wf))

		def apply(rt: ContentType, rrt: ContentType, wf: RDFWriterFactory) =
			new ProtocolOption(rt, rrt, new GraphQueryEvaluator(wf))
	}

	val tupleQueryProtocolOptions: List[ProtocolOption[TupleQuery]] =
		ProtocolOption(jsonSparql, jsonSparql, jsonSparqlWriterFactory) ::
		ProtocolOption(jsonSparql, ContentTypes.`application/json`, jsonSparqlWriterFactory) ::
		ProtocolOption(xmlSparql, xmlSparql, xmlSparqlWriterFactory) ::
		ProtocolOption(xmlSparql, xml, xmlSparqlWriterFactory) ::
		ProtocolOption(csvSparql, ContentTypes.`text/csv(UTF-8)`, csvSparqlWriterFactory) ::
		ProtocolOption(tsvSparql, ContentTypes.`text/plain(UTF-8)`, tsvSparqlWriterFactory) ::
		Nil

	import InstanceServerSerializer.{xmlContType, turtleContType}

	val graphQueryProtocolOptions: List[ProtocolOption[GraphQuery]] =
		ProtocolOption(xmlContType, xml, xmlRdfWriterFactory) ::
		ProtocolOption(xmlContType, xmlContType, xmlRdfWriterFactory) ::
		ProtocolOption(turtleContType, ContentTypes.`text/plain(UTF-8)`, turtleRdfWriterFactory) ::
		ProtocolOption(turtleContType, turtleContType, turtleRdfWriterFactory) ::
		Nil

	def plainResponse(status: StatusCode, responseText: String): List[Marshalling[HttpResponse]] =
		Marshalling.Opaque(
			() => HttpResponse(status = status, entity = responseText)
		) :: Nil

}
