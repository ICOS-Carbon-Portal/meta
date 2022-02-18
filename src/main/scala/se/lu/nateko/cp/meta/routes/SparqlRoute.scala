package se.lu.nateko.cp.meta.routes

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.CachingDirectives._
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
import se.lu.nateko.cp.meta.utils.getStackTrace
import akka.http.scaladsl.server.RequestContext
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.HttpEntity.Default
import java.security.MessageDigest
import akka.http.caching.LfuCache
import akka.http.scaladsl.server.RouteResult
import akka.actor.ActorSystem
import scala.util.Random
import akka.http.caching.scaladsl.Cache
import se.lu.nateko.cp.meta.utils.streams.CachedSource

object SparqlRoute {

	val getClientIp: Directive1[Option[String]] = optionalHeaderValueByName(`X-Forwarded-For`.name)

	def apply()(implicit marsh: ToResponseMarshaller[SparqlQuery], envriConfigs: EnvriConfigs, system: ActorSystem): Route = {

		val makeResponse: String => Route = query => respondWithHeaders(`Access-Control-Allow-Origin`.*) {
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

		val plainRoute =
			get{
				parameter("query")(makeResponse) ~
				complete(StatusCodes.BadRequest -> (
					"Expected a SPARQL query provided as 'query' URL parameter.\n" +
					"Alternatively, the query can be HTTP POSTed as 'query' Form field or as a plain text payload\n" +
					"See the specification at https://www.w3.org/TR/sparql11-protocol/#query-operation\n" +
					"Human users may want to use the Web app at https://meta.icos-cp.eu/sparqlclient/"
				))
			} ~
			post{
				formField("query")(makeResponse) ~
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

		val spCache = new SparqlCache(system)

		path("sparql"){
			//entity(as[ByteString]){payload =>
				cache(spCache, spCache.cacheKeyer){
					plainRoute
				}
			//}
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

		val (respMat, queue) = resp.entity.dataBytes.toMat(Sink.queue[ByteString]())(Keep.both).run()

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
						HttpResponse(StatusCodes.InternalServerError, entity = err.getMessage + "\n" + getStackTrace(err))
				}

			case Some(first) =>
				val restSrc = Source.unfoldAsync(queue)(q => q.pull().map(_.map(q -> _)))

				val data = Source.combine(Source.single(first), restSrc)(Concat.apply(_))
					.watchTermination()((_, done) => {
						done.onComplete(_ => queue.cancel())
					})

				respondWith(data)
		}
	}

}

class SparqlCache(system: ActorSystem)(implicit mat: Materializer) extends Cache[Sha256Sum, RouteResult]{

	private[this] val inner = LfuCache.apply[Sha256Sum, RouteResult](system)

	override def apply(key: Sha256Sum, genValue: () => Future[RouteResult]): Future[RouteResult] = {
		import system.dispatcher
		inner.apply(key, () => genValue().flatMap(makeCached))
	}

	override def getOrLoad(key: Sha256Sum, loadValue: Sha256Sum => Future[RouteResult]): Future[RouteResult] = {
		import system.dispatcher
		inner.getOrLoad(key, hash => loadValue(hash).flatMap(makeCached))
	}

	override def get(key: Sha256Sum): Option[Future[RouteResult]] = inner.get(key)

	override def put(key: Sha256Sum, mayBeValue: Future[RouteResult])(implicit ex: ExecutionContext): Future[RouteResult] = {
		inner.put(key, mayBeValue.flatMap(makeCached))
	}

	override def remove(key: Sha256Sum): Unit = inner.remove(key)

	override def clear(): Unit = inner.clear()

	override def keys: Set[Sha256Sum] = inner.keys

	override def size(): Int = inner.size()

	val cacheKeyer: PartialFunction[RequestContext, Sha256Sum] = {
		case reqCtxt if shouldCache(reqCtxt) =>
			val req = reqCtxt.request
			val accept = req.header[Accept].fold("")(_.value())
			val query = req.uri.rawQueryString.getOrElse("")
			val payload = req.entity match{
				case Strict(_, data) => data
				//randomize cache key to prevent caching in other cases
				case _ => ByteString(Random.nextBytes(32))
			}
			val digest = MessageDigest.getInstance("SHA-256")
			digest.update(accept.getBytes())
			digest.update(query.getBytes())
			payload.asByteBuffers.foreach(digest.update)
			val key = new Sha256Sum(digest.digest())
			println(key)
			key
	}

	private def shouldCache(ctxt: RequestContext): Boolean = {
		val meth = ctxt.request.method
		meth == HttpMethods.GET || meth == HttpMethods.POST
	}

	private def makeCached(rr: RouteResult)(implicit ex: ExecutionContext): Future[RouteResult] = rr match {
		case _: Rejected => Future.successful(rr)
		case Complete(response) =>
			response.entity match {
				case _: Strict => Future.successful(rr)
				case ent =>
					val cachedPayload = CachedSource(ent.dataBytes)
					val cachedEnt = HttpEntity.CloseDelimited(ent.contentType, cachedPayload)
					Future.successful(Complete(response.withEntity(cachedEnt)))
			}
	}
}
