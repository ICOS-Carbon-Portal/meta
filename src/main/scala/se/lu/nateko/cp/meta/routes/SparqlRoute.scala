package se.lu.nateko.cp.meta.routes

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.RouteResult.Rejected
import akka.stream.Materializer
import akka.stream.scaladsl.Concat
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs

object SparqlRoute {

	val setSparqlHeaders = respondWithHeaders(
		`Access-Control-Allow-Origin`.*,
		`Cache-Control`(CacheDirectives.`no-cache`, CacheDirectives.`no-store`, CacheDirectives.`must-revalidate`)
	)

	val getClientIp: Directive1[Option[String]] = optionalHeaderValueByName(`X-Forwarded-For`.name)

	def apply()(implicit marsh: ToResponseMarshaller[SparqlQuery], envriConfigs: EnvriConfigs): Route = {

		val makeResponse: String => Route = query => setSparqlHeaders {
			handleExceptions(MainRoute.exceptionHandler){
				handleRejections(RejectionHandler.default){
					getClientIp{ip =>
						ensureNoEmptyOkResponseDueToTimeout{
							complete(SparqlQuery(query, ip))
						}
					}
				}
			}
		}

		pathPrefix("sparql"){
			get{
				parameter('query)(makeResponse)
			} ~
			post{
				formField('query)(makeResponse) ~
				entity(as[String])(makeResponse)
			} ~
			options{
				respondWithHeaders(
					`Access-Control-Allow-Origin`.*,
					`Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST),
					`Access-Control-Allow-Headers`(`Content-Type`.name, `Cache-Control`.name)
				){
					complete(StatusCodes.OK)
				}
			}
		}
	}

	private val ensureNoEmptyOkResponseDueToTimeout: Directive0 = extractRequestContext.flatMap{ctxt =>
		import ctxt.{executionContext, materializer}

		mapRouteResultWith{
			case rejected: Rejected =>
				Future.successful(rejected)
			case Complete(resp) =>
				delayResponseUntilStreamingStarts(resp).map(Complete.apply)
		}
	}

	private def delayResponseUntilStreamingStarts(resp: HttpResponse)(
			implicit mat: Materializer, exe: ExecutionContext
	): Future[HttpResponse] = {

		val (respMat, queue) = resp.entity.dataBytes.toMat(Sink.queue[ByteString])(Keep.both).run()

		def respondWith(data: Source[ByteString, Any]) = resp.withEntity(HttpEntity(resp.entity.contentType, data))

		queue.pull().map{

			case None => //empty original entity
				val sparqlErr: Option[Throwable] = respMat match{
					case fut: Future[Any] => fut.value.flatMap(_.failed.toOption)
					case _ => None
				}

				sparqlErr.fold(respondWith(Source.empty)){
					case cexc: CompletionException if(cexc.getCause.isInstanceOf[CancellationException]) =>
						HttpResponse(StatusCodes.RequestTimeout, entity = "SPARQL execution timeout")
					case err: Throwable =>
						HttpResponse(StatusCodes.InternalServerError, entity = err.getMessage)
				}

			case Some(first) =>
				val restSrc = Source.unfoldAsync(queue)(q => q.pull().map(_.map(q -> _)))

				val data = Source.combine(Source.single(first), restSrc)(Concat.apply)
					.watchTermination()((_, done) => {
						done.onComplete(_ => queue.cancel())
					})

				respondWith(data)
		}
	}
}
