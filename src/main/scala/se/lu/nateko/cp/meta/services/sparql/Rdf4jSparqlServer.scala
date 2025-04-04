package se.lu.nateko.cp.meta.services.sparql

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToResponseMarshaller}
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpCharsets, HttpEntity, HttpResponse, MediaType, MediaTypes, StatusCode, StatusCodes}
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.ByteString
import akka.{Done, NotUsed}
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.query.parser.{ParsedBooleanQuery, ParsedGraphQuery, ParsedTupleQuery}
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

import java.time.Instant
import java.util.concurrent.{CancellationException, Executors}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try


class Rdf4jSparqlServer(
	repo: Repository, config: SparqlServerConfig)(using system: ActorSystem) extends SparqlServer:
	import Rdf4jSparqlServer.*

	private val log = Logging.getLogger(system, this)
	private val sparqlExe = Executors.newCachedThreadPool() //.newFixedThreadPool(3)
	private val quoter = new QuotaManager(config, sparqlExe)((() => Instant.now()))
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
			val sparqlEntityBytes: Source[ByteString, NotUsed] = StreamConverters.asOutputStream(timeout).mapMaterializedValue{ outStr =>

				val conn = repo.getConnection()

				val (closer, sparqlFut) = Try:
						conn.prepareQuery(queryStr.query).asInstanceOf[Q]
					.flatMap: query =>
						val sparqlCtxt = ExecutionContext.fromExecutor(qquoter)
						protocolOption.evaluator.evaluate(query, outStr)(using sparqlCtxt)
					.fold(
						err =>
							val nopCloser = new AutoCloseable:
								def close(): Unit = ()

							nopCloser -> Future.failed[Done](err)
						,
						(closer, doneFut) =>
							system.scheduler.scheduleOnce(config.maxQueryRuntimeSec.seconds):
								if !doneFut.isCompleted then
									if qquoter.keepRunningIndefinitely then
										log.info(s"Permitting long-running query ${qquoter.qid} from client ${qquoter.cid}")
									else
										log.info(s"Terminating long-running query ${qquoter.qid} from client ${qquoter.cid}")
										errPromise.tryFailure(CancellationException())
										closer.close()
							closer -> doneFut
					)

				sparqlFut.onComplete: tryDone =>
					errPromise.tryComplete(tryDone.map(_ => ByteString.empty))
					try
						outStr.flush()
						outStr.close()
					catch case _: Throwable =>
						log.debug("SPARQL stream was closed/detached")
					finally
						qquoter.logQueryFinish()
						conn.close()

				closer
			}.wireTap:
				Sink.head[ByteString].mapMaterializedValue(
					_.foreach(_ => qquoter.logQueryStreamingStart())
				)
			.watchTermination(): (closer, doneFut) =>
				doneFut.onComplete(doneTry =>
					closer.close()
				)
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
