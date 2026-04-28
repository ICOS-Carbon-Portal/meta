package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToResponseMarshaller}
import akka.http.scaladsl.model.*
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.eclipse.rdf4j.query.MalformedQueryException
import org.eclipse.rdf4j.query.parser.ParsedGraphQuery
import org.eclipse.rdf4j.query.parser.ParsedTupleQuery
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.{SparqlQuery, SparqlServer}
import se.lu.nateko.cp.meta.services.CpmetaVocab

import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

class HttpRdfSparqlServer(client: HttpRdfStoreClient, config: SparqlServerConfig)(using system: ActorSystem, mat: Materializer) extends SparqlServer:
	import Rdf4jSparqlServer.{tupleQueryProtocolOptions, graphQueryProtocolOptions, plainResponse}

	private given ExecutionContext = system.dispatcher
	private val parser = new SPARQLParser()

	override def shutdown(): Unit = ()

	override def marshaller: ToResponseMarshaller[SparqlQuery] = Marshaller(
		exeCtxt => query => Future(getMarshallings(query))(exeCtxt)
	)

	private def getMarshallings(query: SparqlQuery): List[Marshalling[HttpResponse]] =
		try
			parser.parseQuery(query.query, CpmetaVocab.MetaPrefix) match
				case _: ParsedTupleQuery =>
					tupleQueryProtocolOptions.map(opt => proxyMarshalling(query.query, opt.requestedResponseType, opt.responseType))
				case _: ParsedGraphQuery =>
					graphQueryProtocolOptions.map(opt => proxyMarshalling(query.query, opt.requestedResponseType, opt.responseType))
				case _ =>
					plainResponse(StatusCodes.NotImplemented, "Unsupported query type")
		catch case err: MalformedQueryException =>
			plainResponse(StatusCodes.BadRequest, err.getMessage)

	private def proxyMarshalling(
		query: String, requestedType: ContentType, responseType: ContentType
	): Marshalling[HttpResponse] =
		Marshalling.WithFixedContentType(
			requestedType,
			() =>
				val entitySource: Source[ByteString, NotUsed] = Source
					.futureSource(
						client.sparqlQuery(query, responseType.mediaType.toString()).flatMap: resp =>
							if resp.status.isSuccess() then
								Future.successful(resp.entity.dataBytes)
							else
								resp.entity.toStrict(30.seconds).flatMap: strict =>
									Future.failed(Exception(s"SPARQL proxy error ${resp.status}: ${strict.data.utf8String}"))
					)
					.mapMaterializedValue(_ => NotUsed)
				HttpResponse(entity = HttpEntity(responseType, entitySource))
		)

end HttpRdfSparqlServer
