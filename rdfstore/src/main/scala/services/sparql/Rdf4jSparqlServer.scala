package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToResponseMarshaller}
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpCharsets, HttpEntity, HttpResponse, MediaType, MediaTypes, StatusCode, StatusCodes}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.query.parser.{ParsedBooleanQuery, ParsedGraphQuery, ParsedTupleQuery, ParsedQuery}
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterFactory
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriterFactory
import org.eclipse.rdf4j.query.resultio.sparqlxml.SPARQLResultsXMLWriterFactory
import org.eclipse.rdf4j.query.resultio.text.csv.SPARQLResultsCSVWriterFactory
import org.eclipse.rdf4j.query.resultio.text.tsv.SPARQLResultsTSVWriterFactory
import org.eclipse.rdf4j.query.{GraphQuery, MalformedQueryException, Query, TupleQuery}
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection}
import org.eclipse.rdf4j.rio.RDFWriterFactory
import org.eclipse.rdf4j.rio.rdfxml.RDFXMLWriterFactory
import org.eclipse.rdf4j.rio.turtle.TurtleWriterFactory
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.{SparqlQuery, SparqlServer}
import se.lu.nateko.cp.meta.services.CpmetaVocab

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CancellationException, Executors}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, Promise}


class Rdf4jSparqlServer(
	repo: Repository, config: SparqlServerConfig)(using system: ActorSystem) extends SparqlServer:
	import Rdf4jSparqlServer.*

	private val log = Logging.getLogger(system, this)
	private val sparqlExe = Executors.newCachedThreadPool() //.newFixedThreadPool(3)
	private val quoter = new QuotaManager(config, sparqlExe)(Instant.now _)
	private given ExecutionContext = system.dispatcher

	//QuotaManager should be cleaned periodically to forget very old query runs
	system.scheduler.scheduleWithFixedDelay(1.hour, 1.hour)(() => quoter.cleanup())

	def shutdown(): Unit = {
		sparqlExe.shutdown()
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

				case _: ParsedQuery =>
					plainResponse(StatusCodes.NotImplemented, "Unsupported query")

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
			val qquoter = quoter.getQueryQuotaManager(queryStr.clientId)
			val errPromise = Promise[ByteString]()
			// `errPromise` ends the response by the deadline, even for a query still waiting for admission
			// (possibly forever, if the quota manager drops it from the queue). Ending the response cancels
			// the query source, which then stops reading the store promptly (see QueryStreaming).
			val run = QueryRun(qquoter)
			val sparqlCtxt = ExecutionContext.fromExecutor(qquoter)

			//the query is admitted to run by the per-client quota manager
			val sparqlEntityBytes: Source[ByteString, NotUsed] = Source
				.lazyFuture(() => Future(Done)(using sparqlCtxt))
				.flatMapConcat { _ =>
					protocolOption.evaluator
						.source(() => run.open(repo), queryStr.query, QueryDispatcher, run.finish)
				}
				.wireTap {
					Sink.head[ByteString].mapMaterializedValue(
						_.foreach(_ => qquoter.logQueryStreamingStart())
					)
				}
				.watchTermination() { (_, doneFut) =>
					system.scheduler.scheduleOnce(config.maxQueryRuntimeSec.seconds) {
						if (!doneFut.isCompleted) {
							if (qquoter.keepRunningIndefinitely) {
								log.info(s"Permitting long-running query ${qquoter.qid} from client ${qquoter.cid}")
							} else {
								log.info(s"Terminating long-running query ${qquoter.qid} from client ${qquoter.cid}")
								errPromise.tryFailure(CancellationException())
							}
						}
					}
					doneFut.onComplete { doneTry =>
						errPromise.tryComplete(doneTry.map(_ => ByteString.empty))
						// if the query was opened, it is finished when the query source has closed it
						run.finishIfNeverOpened()
					}
					NotUsed
				}

			val entityBytes = sparqlEntityBytes.merge(Source.future(errPromise.future))

			HttpResponse(entity = HttpEntity(protocolOption.responseType, entityBytes))
		}
	)
end Rdf4jSparqlServer

/**
 * Makes sure the query run is logged as finished exactly once: by the query source after it has closed
 * the query (so that its quota slot is not freed while it still reads the store), or upon stream
 * termination if the query never started (e.g. dropped from the queue by the quota manager).
 */
private class QueryRun(qquoter: QuotaManager.QueryQuotaManager) {
	import QueryRun.*
	private val state = AtomicInteger(Pending)

	def open(repo: Repository): RepositoryConnection = {
		if (state.compareAndSet(Pending, Opened)) repo.getConnection()
		else throw CancellationException("SPARQL query was cancelled before it started")
	}

	def finish(): Unit = {
		if (state.getAndSet(Finished) != Finished) qquoter.logQueryFinish()
	}

	def finishIfNeverOpened(): Unit = {
		if (state.compareAndSet(Pending, Finished)) qquoter.logQueryFinish()
	}
}

private object QueryRun {
	private val Pending = 0
	private val Opened = 1
	private val Finished = 2
}

object Rdf4jSparqlServer:

	/** Dispatcher for the blocking evaluation of SPARQL queries, configured in application.conf */
	val QueryDispatcher = "cpmeta-sparql-query-dispatcher"

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

end Rdf4jSparqlServer
