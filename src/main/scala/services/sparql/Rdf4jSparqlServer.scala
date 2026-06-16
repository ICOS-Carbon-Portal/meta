package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToResponseMarshaller}
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpCharsets, HttpEntity, HttpResponse, MediaType, MediaTypes, StatusCode, StatusCodes}
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
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
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.rio.RDFWriterFactory
import org.eclipse.rdf4j.rio.rdfxml.RDFXMLWriterFactory
import org.eclipse.rdf4j.rio.turtle.TurtleWriterFactory
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.{SparqlQuery, SparqlServer}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.QuotaManager.QueryQuotaManager

import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CancellationException, Executors}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


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

	private def shortHash(s: String): String =
		val md = MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))
		md.take(8).map("%02x".format(_)).mkString

	private final class LoggedCloseOnce(name: String, closeable: AutoCloseable, logFailure: String => Unit) extends AutoCloseable:
		private val closed = new AtomicBoolean(false)
		override def close(): Unit =
			if closed.compareAndSet(false, true) then
				try closeable.close()
				catch case NonFatal(err) => logFailure(s"Failed to close $name: ${err.getClass.getName}: ${err.getMessage}")

	private final class QueryRunFinalizer(qquoter: QueryQuotaManager):
		private val finished = new AtomicBoolean(false)
		def finish(): Unit =
			if finished.compareAndSet(false, true) then qquoter.logQueryFinish()

	private def getQueryMarshalling[Q <: Query](
		queryStr: SparqlQuery,
		protocolOption: ProtocolOption[Q]
	): Marshalling[HttpResponse] = Marshalling.WithFixedContentType(
		protocolOption.requestedResponseType,
		() => {
			val streamTimeout = (config.maxQueryRuntimeSec + 1).seconds
			val qquoter = quoter.getQueryQuotaManager(queryStr.clientId)
			val queryHash = shortHash(queryStr.query)
			val errPromise = Promise[ByteString]()
			val finalizer = new QueryRunFinalizer(qquoter)
			val permittedLongRunning = new AtomicBoolean(false)
			log.info(s"SPARQL query started qid=${qquoter.qid} client=${qquoter.cid} hash=$queryHash responseType=${protocolOption.responseType}")

			val sparqlEntityBytes: Source[ByteString, NotUsed] = StreamConverters.asOutputStream(streamTimeout).mapMaterializedValue{ outStr =>

				val conn = repo.getConnection()
				val connCloser = new LoggedCloseOnce(s"SPARQL repository connection qid=${qquoter.qid}", conn, log.debug)
				val streamCloser = new LoggedCloseOnce(s"SPARQL output stream qid=${qquoter.qid}", new AutoCloseable:
					def close(): Unit =
						outStr.flush()
						outStr.close()
				, log.debug)

				val (resultCloser, sparqlFut) = Try:
						val query = conn.prepareQuery(queryStr.query).asInstanceOf[Q]
						query.setMaxExecutionTime(config.maxQueryRuntimeSec)
						query
					.flatMap: query =>
						val sparqlCtxt = ExecutionContext.fromExecutor(qquoter)
						protocolOption.evaluator.evaluate(query, outStr)(using sparqlCtxt)
					.fold(
						err =>
							log.warning(s"SPARQL query prepare/evaluate failed qid=${qquoter.qid} client=${qquoter.cid} hash=$queryHash error=${err.getClass.getName}: ${err.getMessage}")
							(new AutoCloseable:
								def close(): Unit = ()
							) -> Future.failed[Done](err)
						,
						(closer, doneFut) =>
							val loggedCloser = new LoggedCloseOnce(s"SPARQL result qid=${qquoter.qid}", closer, log.debug)
							system.scheduler.scheduleOnce(config.maxQueryRuntimeSec.seconds):
								if !doneFut.isCompleted then
									if qquoter.keepRunningIndefinitely then
										permittedLongRunning.set(true)
										log.info(s"SPARQL query permitted to keep running qid=${qquoter.qid} client=${qquoter.cid} hash=$queryHash")
									else
										log.warning(s"SPARQL query exceeded runtime qid=${qquoter.qid} client=${qquoter.cid} hash=$queryHash maxRuntimeSec=${config.maxQueryRuntimeSec} action=waiting-for-rdf4j-timeout")
							system.scheduler.scheduleOnce((config.maxQueryRuntimeSec + 10).seconds):
								if !doneFut.isCompleted && !permittedLongRunning.get() then
									log.error(s"SPARQL query still running after grace period qid=${qquoter.qid} client=${qquoter.cid} hash=$queryHash action=force-closing-result")
									errPromise.tryFailure(CancellationException(s"SPARQL query ${qquoter.qid} timed out"))
									loggedCloser.close()
							loggedCloser -> doneFut
					)

				sparqlFut.onComplete: tryDone =>
					tryDone match
						case Success(_) =>
							log.info(s"SPARQL query completed qid=${qquoter.qid} client=${qquoter.cid} hash=$queryHash")
						case Failure(err) =>
							log.warning(s"SPARQL query failed qid=${qquoter.qid} client=${qquoter.cid} hash=$queryHash error=${err.getClass.getName}: ${err.getMessage}")
					errPromise.tryComplete(tryDone.map(_ => ByteString.empty))
					streamCloser.close()
					finalizer.finish()
					connCloser.close()

				resultCloser
			}.wireTap:
				Sink.head[ByteString].mapMaterializedValue(
					_.foreach(_ =>
						log.info(s"SPARQL query streaming started qid=${qquoter.qid} client=${qquoter.cid} hash=$queryHash")
						qquoter.logQueryStreamingStart()
					)
				)
			.watchTermination(): (closer, doneFut) =>
				doneFut.onComplete: doneTry =>
					if !doneTry.isSuccess then
						log.debug(s"SPARQL response stream terminated qid=${qquoter.qid} client=${qquoter.cid} hash=$queryHash result=$doneTry")
					closer.close()
					finalizer.finish()
				NotUsed

			val entityBytes = sparqlEntityBytes.merge(Source.future(errPromise.future))

			HttpResponse(entity = HttpEntity(protocolOption.responseType, entityBytes))
		}
	)
end Rdf4jSparqlServer

object Rdf4jSparqlServer:

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
