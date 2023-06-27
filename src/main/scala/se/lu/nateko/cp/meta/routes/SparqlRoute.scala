package se.lu.nateko.cp.meta.routes

import akka.actor.ActorSystem
import akka.http.caching.LfuCache
import akka.http.caching.scaladsl.Cache
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.HttpEntity.Default
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.*
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.RouteResult.Rejected
import akka.http.scaladsl.server.directives.CachingDirectives.*
import akka.stream.Materializer
import akka.stream.scaladsl.Concat
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.CacheSizeLimitExceeded
import se.lu.nateko.cp.meta.utils.getStackTrace
import se.lu.nateko.cp.meta.utils.streams.CachedSource

import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.Random
import scala.util.Success

object SparqlRoute {

	val X_Cache_Status = "X-Cache-Status"

	val getClientIp: Directive1[Option[String]] = optionalHeaderValueByName(`X-Forwarded-For`.name)

	val withPermissiveCorsHeader: Directive0 = optionalHeaderValueByType(Origin).tflatMap{
		case Tuple1(Some(orHeader)) =>
			val headers = orHeader.origins.map(`Access-Control-Allow-Origin`.apply)
			respondWithHeaders(headers)
		case Tuple1(None) => respondWithHeaders(`Access-Control-Allow-Origin`.*)
	}

	def apply(conf: SparqlServerConfig)(using marsh: ToResponseMarshaller[SparqlQuery], envriConfigs: EnvriConfigs, system: ActorSystem): Route = {

		val makeResponse: String => Route = query => withPermissiveCorsHeader{
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

		val badRequestResponse: Route = withPermissiveCorsHeader{
			complete(StatusCodes.BadRequest -> (
				"Expected a SPARQL query provided as 'query' URL parameter.\n" +
				"Alternatively, the query can be HTTP POSTed as 'query' Form field or as a plain text payload\n" +
				"See the specification at https://www.w3.org/TR/sparql11-protocol/#query-operation\n" +
				"Human users may want to use the Web app at https://meta.icos-cp.eu/sparqlclient/"
			))
		}

		val plainRoute =
			get{
				parameter("query")(makeResponse) ~
				badRequestResponse
			} ~
			post{
				formField("query")(makeResponse) ~
				entity(as[String])(makeResponse) ~
				badRequestResponse
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

		val spCache = new SparqlCache(system, conf.maxCacheableQuerySize)
		val bypass = respondWithHeader(RawHeader(X_Cache_Status, "BYPASS")){plainRoute}

		path("sparql"){
			cachingProhibited{bypass} ~
			extractRequestContext{ctxt =>
				spCache.cacheKeyer.lift(ctxt).fold(bypass){key =>
					val cacheStat = if(spCache.keys.contains(key)) "HIT" else "MISS"
					respondWithHeader(RawHeader(X_Cache_Status, cacheStat)){
						withPermissiveCorsHeader{//is applied on per-origin basis, so cannot cache these
							_ => spCache.apply(key, () => plainRoute(ctxt))
						}
					}
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

		val (respMat, queue) = resp.entity.dataBytes.toMat(Sink.queue[ByteString]())(Keep.both).run()

		def respondWith(data: Source[ByteString, Any]) = resp.withEntity(HttpEntity(resp.entity.contentType, data))

		queue.pull().map{

			case None => //empty original entity
				val sparqlErr: Option[Throwable] = respMat match{
					case fut: Future[Any] => fut.value.flatMap(_.failed.toOption)
					case _ => None
				}

				sparqlErr.fold(respondWith(Source.empty)){
					case _: CancellationException =>
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

class SparqlCache(system: ActorSystem, maxCacheableQuerySize: Int)(using Materializer) extends Cache[Sha256Sum, RouteResult]{

	private val inner = LfuCache.apply[Sha256Sum, RouteResult](using system)
	val quota = new CachedSource.Quota[ByteString](_.length, maxCacheableQuerySize)

	override def apply(key: Sha256Sum, genValue: () => Future[RouteResult]): Future[RouteResult] =
		import system.dispatcher
		removeInappropriate(key)
		inner.apply(key, () => genValue().map(makeCached(key)))

	override def getOrLoad(key: Sha256Sum, loadValue: Sha256Sum => Future[RouteResult]): Future[RouteResult] =
		import system.dispatcher
		removeInappropriate(key)
		inner.getOrLoad(key, hash => loadValue(hash).map(makeCached(key)))

	override def get(key: Sha256Sum): Option[Future[RouteResult]] =
		removeInappropriate(key)
		inner.get(key)

	override def put(key: Sha256Sum, mayBeValue: Future[RouteResult])(using ExecutionContext): Future[RouteResult] = {
		val fresh = mayBeValue.map(makeCached(key))
		inner.put(key, fresh)
		fresh
	}

	override def remove(key: Sha256Sum): Unit = inner.remove(key)

	override def clear(): Unit = inner.clear()

	override def keys: Set[Sha256Sum] = inner.keys

	override def size(): Int = inner.size()

	val cacheKeyer: PartialFunction[RequestContext, Sha256Sum] = {
		case reqCtxt if shouldCache(reqCtxt) =>
			val req = reqCtxt.request
			val accept = req.header[Accept].map(_.mediaRanges.map(_.mainType)).fold("")(_.sorted.mkString)
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
			new Sha256Sum(digest.digest())
	}

	private def shouldCache(ctxt: RequestContext): Boolean = {
		val meth = ctxt.request.method
		meth == HttpMethods.GET || meth == HttpMethods.POST
	}

	private def removeInappropriate(key: Sha256Sum): Unit = inner.get(key) match
		case None => () //not cached, nothing to remove
		case Some(fut) => fut.value match
			case None => () //still executing
			case Some(Success(Complete(resp))) =>
				val status = resp.status
				val shouldBeCached = status.isSuccess() && status != StatusCodes.NoContent
				if !shouldBeCached then remove(key)
			case _ => remove(key)

	private def makeCached(key: Sha256Sum)(rr: RouteResult)(using ExecutionContext): RouteResult = rr match
		case _: Rejected => rr
		case Complete(response) =>
			val cachedEnt = response.entity match
				case se: Strict => se
				case ent =>
					val cachedPayload = CachedSource(ent.dataBytes, quota).recover{
						case CacheSizeLimitExceeded =>
							remove(key)
							ByteString("\nSPARQL RESPONSE TOO LARGE TO BE CACHED.\n" +
							s"The largest cacheable response size is ${quota.maxCost} bytes.\n" +
							"Try running the query with 'Cache-Control: no-cache' to get full response\n")
					}
					HttpEntity.CloseDelimited(ent.contentType, cachedPayload)
			Complete(
				response
					.withEntity(cachedEnt)
					//this header is specific for the web app that makes the query, so should not be cached
					.mapHeaders(_.filterNot(_.name == `Access-Control-Allow-Origin`.name))
			)
	end makeCached
}
