package se.lu.nateko.cp.meta.services.sparql

import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import org.eclipse.rdf4j.query.BooleanQuery
import org.eclipse.rdf4j.query.GraphQuery
import org.eclipse.rdf4j.query.MalformedQueryException
import org.eclipse.rdf4j.query.Query
import org.eclipse.rdf4j.query.TupleQuery
import org.eclipse.rdf4j.query.parser.ParsedTupleQuery
import org.eclipse.rdf4j.query.parser.ParsedGraphQuery
import org.eclipse.rdf4j.query.parser.ParsedBooleanQuery
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterFactory
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriterFactory
import org.eclipse.rdf4j.query.resultio.sparqlxml.SPARQLResultsXMLWriterFactory
import org.eclipse.rdf4j.query.resultio.text.csv.SPARQLResultsCSVWriterFactory
import org.eclipse.rdf4j.query.resultio.text.tsv.SPARQLResultsTSVWriterFactory
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.rio.RDFWriterFactory
import org.eclipse.rdf4j.rio.rdfxml.RDFXMLWriterFactory
import org.eclipse.rdf4j.rio.turtle.TurtleWriterFactory

import akka.event.LoggingAdapter
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
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.api.SparqlServer
import se.lu.nateko.cp.meta.services.CpmetaVocab
import scala.concurrent.Promise
import java.util.concurrent.CancellationException


class Rdf4jSparqlServer(repo: Repository, config: SparqlServerConfig, log: LoggingAdapter) extends SparqlServer{
	import Rdf4jSparqlServer.*

	private val sparqlExe = Executors.newCachedThreadPool() //.newFixedThreadPool(3)
	private val canceller = Executors.newSingleThreadScheduledExecutor()
	private val scalaCanceller = scala.concurrent.ExecutionContext.fromExecutorService(canceller)
	private val quoter = new QuotaManager(config, sparqlExe)(Instant.now _)

	//QuotaManager should be cleaned periodically to forget very old query runs
	canceller.scheduleWithFixedDelay(() => quoter.cleanup(), 1, 1, TimeUnit.HOURS)

	def shutdown(): Unit = {
		sparqlExe.shutdown()
		scalaCanceller.shutdown()
		canceller.shutdown()
	}

	def marshaller: ToResponseMarshaller[SparqlQuery] = Marshaller(
		exeCtxt => query => Future{
			quoter.quotaExcess(query.clientId).fold{
				getSparqlingMarshallings(query)
			}{
				plainResponse(StatusCodes.ServiceUnavailable, _)
			}
		}(exeCtxt) //using Akka-provided ExecutionContext to handle the "outer shell" part of response marshalling
		           //(that is, everything except the actual SPARQL query evaluation, which is done by sparqlExe thread pool)
	)

	private def getSparqlingMarshallings(query: SparqlQuery): List[Marshalling[HttpResponse]] = try{
			new SPARQLParser().parseQuery(query.query, CpmetaVocab.MetaPrefix) match {
				case _: ParsedTupleQuery =>
					tupleQueryProtocolOptions.map(getQueryMarshalling(query, _))

				case _: ParsedGraphQuery =>
					graphQueryProtocolOptions.map(getQueryMarshalling(query, _))

				case _: ParsedBooleanQuery =>
					plainResponse(StatusCodes.NotImplemented, "Boolean queries are not supported yet")
			}
		} catch {
			case userErr: MalformedQueryException =>
				plainResponse(StatusCodes.BadRequest, userErr.getMessage)
		}

	private def getQueryMarshalling[Q <: Query](
		queryStr: SparqlQuery,
		protocolOption: ProtocolOption[Q]
	): Marshalling[HttpResponse] = Marshalling.WithFixedContentType(
		protocolOption.requestedResponseType,
		() => {
			val timeout = (config.maxQueryRuntimeSec + 1).seconds
			val qquoter = quoter.getQueryQuotaManager(queryStr.clientId)
			val errPromise = Promise[ByteString]()
			val sparqlEntityBytes: Source[ByteString, Future[Any]] = StreamConverters.asOutputStream(timeout).mapMaterializedValue{ outStr =>

				val conn = repo.getConnection()

				val sparqlFut = CompletableFuture.runAsync(
					() => {
						try
							val query = conn.prepareQuery(queryStr.query).asInstanceOf[Q]
							protocolOption.evaluator.evaluate(query, outStr)
						catch case err =>
							outStr.flush()
							log.error(err, "SPARQL evaluation error")
							errPromise.tryFailure(err)
					},
					qquoter
				)

				val cancelling: Runnable = () =>
					if(!sparqlFut.isDone){
						if(qquoter.keepRunningIndefinitely)
							log.info(s"Permitting long-running query ${qquoter.qid} from client ${qquoter.cid}")
						else{
							log.info(s"Terminating long-running query ${qquoter.qid} from client ${qquoter.cid}")
							errPromise.tryFailure(CancellationException())
							sparqlFut.cancel(true)
						}
					}
				canceller.schedule(cancelling, config.maxQueryRuntimeSec.toLong, TimeUnit.SECONDS)

				sparqlFut.whenCompleteAsync((_, err) =>
					try{outStr.flush(); outStr.close()} finally{
						qquoter.logQueryFinish()
						conn.close()
						if err == null then errPromise.trySuccess(ByteString.empty)
						else errPromise.tryFailure(err)
					},
					sparqlExe
				)
				sparqlFut
			}.wireTap(
				Sink.head[ByteString].mapMaterializedValue(
					_.foreach(_ => qquoter.logQueryStreamingStart())(scalaCanceller)
				)
			).watchTermination(){(sparqlFut, doneFut) =>
				doneFut.onComplete{_ =>
					sparqlFut.cancel(true)
				}(scalaCanceller)
				scala.compat.java8.FutureConverters.toScala(sparqlFut)
			}
			val errSource = Source.future(errPromise.future)
			val entityBytes = errSource.merge(sparqlEntityBytes)

			HttpResponse(entity = HttpEntity(protocolOption.responseType, entityBytes))
		}
	)
}

object Rdf4jSparqlServer{

	private val utf8 = HttpCharsets.`UTF-8`
	private val xml = ContentType(MediaTypes.`application/xml`, utf8)

	val jsonSparql = getSparqlContentType("application/sparql-results+json", ".srj")
	val xmlSparql = getSparqlContentType("application/sparql-results+xml", ".srx")
	val csvSparql = getSparqlContentType("text/csv", ".csv")
	val tsvSparql = getSparqlContentType("text/tab-separated-values", ".tsv")

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

	import se.lu.nateko.cp.meta.services.linkeddata.InstanceServerSerializer.{ turtleContType, xmlContType }

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
