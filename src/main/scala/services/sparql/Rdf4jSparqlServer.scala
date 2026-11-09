package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToResponseMarshaller}
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpCharsets, HttpEntity, HttpResponse, MediaType, MediaTypes, StatusCode, StatusCodes}
import akka.stream.KillSwitches
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
			// Stopping a query takes both of the following, as neither can do the other's job.
			// `cancellation` stops the reading of the store: the query source runs in its own async island
			// and only reacts to stream cancellation between reads, but a single read can take minutes
			// (e.g. ORDER BY materializing all solutions). The flag makes such a read fail promptly,
			// after which the source closes the query result on its own thread.
			val cancellation = QueryCancellation()
			// `killSwitch` ends the response by the deadline: the flag only takes effect where it is checked,
			// so it can't end a query still waiting for admission (possibly forever, if the quota manager
			// drops it from the queue), or one busy in code that doesn't check the flag.
			val killSwitch = KillSwitches.shared(s"sparql-query-${qquoter.qid}")
			val run = QueryRun(qquoter)

			val entityBytes: Source[ByteString, NotUsed] = Source
				.lazyFuture(() => admission(qquoter))
				.flatMapConcat { _ =>
					protocolOption.evaluator
						.source(() => run.open(repo), queryStr.query, cancellation, run.finish)
						.async(QueryDispatcher)
				}
				.via(killSwitch.flow)
				.wireTap(
					Sink.head[ByteString].mapMaterializedValue(
						_.foreach(_ => qquoter.logQueryStreamingStart())
					)
				)
				.watchTermination() { (_, doneFut) =>
					val timer = system.scheduler.scheduleOnce(config.maxQueryRuntimeSec.seconds) {
						if (qquoter.keepRunningIndefinitely) {
							log.info(s"Permitting long-running query ${qquoter.qid} from client ${qquoter.cid}")
						} else {
							log.info(s"Terminating long-running query ${qquoter.qid} from client ${qquoter.cid}")
							// the flag stops the store reads, the kill switch fails the response right away
							cancellation.cancel()
							killSwitch.abort(CancellationException())
						}
					}
					doneFut.onComplete { _ =>
						timer.cancel()
						// in case the stream was cancelled by the client while the query source is stuck in a long read
						cancellation.cancel()
						// otherwise, the query is finished when the query source has closed it
						run.finishIfNeverOpened()
					}
					NotUsed
				}

			HttpResponse(entity = HttpEntity(protocolOption.responseType, entityBytes))
		}
	)

	/** Completes when the per-client quota allows the query to start running */
	private def admission(qquoter: QuotaManager.QueryQuotaManager): Future[Done] = {
		val admitted = Promise[Done]()
		qquoter.execute(() => admitted.trySuccess(Done))
		admitted.future
	}

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
