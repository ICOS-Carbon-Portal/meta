package se.lu.nateko.cp.meta.routes

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.caching.LfuCache
import akka.http.caching.scaladsl.Cache
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.HttpEntity.Default
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.*
import akka.http.scaladsl.server.Directive
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
import akka.stream.SinkShape
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.Concat
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.SinkQueueWithCancel
import akka.stream.scaladsl.Source
import akka.util.ByteString
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.CacheSizeLimitExceeded
import se.lu.nateko.cp.meta.utils.getStackTrace

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.Random
import scala.util.Success

object SparqlRoute:

	val X_Cache_Status = "X-Cache-Status"

	val getClientIp: Directive1[Option[String]] = optionalHeaderValueByName(`X-Forwarded-For`.name)

	val withPermissiveCorsHeader: Directive0 = optionalHeaderValueByType(Origin).tflatMap{
		case Tuple1(Some(orHeader)) =>
			val headers = orHeader.origins.map(`Access-Control-Allow-Origin`.apply)
			respondWithHeaders(headers)
		case Tuple1(None) => respondWithHeaders(`Access-Control-Allow-Origin`.*)
	}

	def apply(conf: SparqlServerConfig)(using marsh: ToResponseMarshaller[SparqlQuery], envriConfigs: EnvriConfigs, system: ActorSystem): Route =

		val makeResponse: String => Route = query =>
			handleExceptions(MainRoute.exceptionHandler):
				handleRejections(RejectionHandler.default):
					getClientIp: ip =>
						ensureNoEmptyOkResponseDueToTimeout:
							complete(SparqlQuery(query, ip))

		val badRequestResponse: Route =
			complete(StatusCodes.BadRequest -> (
				"Expected a SPARQL query provided as 'query' URL parameter.\n" +
				"Alternatively, the query can be HTTP POSTed as 'query' Form field or as a plain text payload\n" +
				"See the specification at https://www.w3.org/TR/sparql11-protocol/#query-operation\n" +
				"Human users may want to use the Web app at https://meta.icos-cp.eu/sparqlclient/"
			))

		val plainRoute =
			get{
				parameter("query")(makeResponse) ~
				badRequestResponse
			} ~
			post{
				formField("query")(makeResponse) ~
				entity(as[String])(makeResponse) ~
				badRequestResponse
			}

		val spCache = SparqlCache(conf.maxCacheableQuerySize)
		val bypass = respondWithHeader(RawHeader(X_Cache_Status, "BYPASS")){plainRoute}

		def cacheStatus(key: Sha256Sum): Directive[Tuple2[String, Boolean]] = cachingProhibited
			.tmap(_ => ("BYPASS", true))
			.or:
				val msg = if(spCache.contains(key)) "HIT" else "MISS"
				tprovide((msg, false))

		path("sparql"):
			withPermissiveCorsHeader:
				options:
					respondWithHeaders(
						`Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST),
						`Access-Control-Allow-Headers`(`Content-Type`.name, `Cache-Control`.name)
					)(complete(StatusCodes.OK))
				~
				extractRequestContext: ctxt =>
					//system.log.info(s"Got a SPARQL request with entity ${ctxt.request.entity}")
					spCache.makeKey(ctxt).fold(bypass): key =>
						cacheStatus(key): (cacheStatusMessage, cacheProhibited) =>
							respondWithHeader(RawHeader(X_Cache_Status, cacheStatusMessage)): _ =>
								if cacheProhibited
								then spCache.put(key, plainRoute(ctxt))
								else spCache.apply(key, () => plainRoute(ctxt))
	end apply

	private val ensureNoEmptyOkResponseDueToTimeout: Directive0 = extractRequestContext.flatMap{ctxt =>
		import ctxt.{executionContext, materializer}

		mapRouteResultWith{
			case rejected: Rejected =>
				Future.successful(rejected)
			case Complete(resp) =>
				handleSparqlFailures(resp).map(Complete.apply)
		}
	}

	def handleSparqlFailures(resp: HttpResponse)(
			using mat: Materializer, exe: ExecutionContext
	): Future[HttpResponse] =
		val peekN = 4
		val (headFut, tailQueue) = resp.entity.dataBytes.toMat(sparqlPeekSink(peekN))(Keep.right).run()

		headFut
			.map: head =>
				val data = if head.size < peekN then Source(head) else Source(head).concat(
					Source.unfoldResourceAsync(
						() =>
							Future.successful(tailQueue),
						_.pull(),
						q =>
							q.cancel()
							Future.successful(akka.Done)
					)
				).recover{
					case err =>
						ByteString.apply("\n" + err.getMessage + "\n" + getStackTrace(err), StandardCharsets.UTF_8)
				}
				resp.withEntity(HttpEntity(resp.entity.contentType, data))
			.recover:
				case _: CancellationException =>
					println("Cancellation detected, responding with timeout")
					HttpResponse(
						StatusCodes.RequestTimeout,
						entity = "SPARQL execution timeout",
						headers = Seq(Connection("close"))
					)
				case err: Throwable =>
					HttpResponse(StatusCodes.InternalServerError, entity = err.getMessage + "\n" + getStackTrace(err))


	private def sparqlPeekSink(n: Int): Sink[ByteString, (Future[Seq[ByteString]], SinkQueueWithCancel[ByteString])] =
		val peek = Flow[ByteString].take(n).toMat(Sink.seq)(Keep.right)
		val tail = Flow[ByteString].drop(n).toMat(Sink.queue())(Keep.right)
		Sink.fromGraph(GraphDSL.createGraph(peek, tail)(Keep.both){implicit b => (s1, s2) =>
			import GraphDSL.Implicits._
			val fork = b.add(Broadcast[ByteString](2, eagerCancel = false))
			fork.out(0) ~> s1
			fork.out(1) ~> s2
			SinkShape(fork.in)
		})

end SparqlRoute

class SparqlCache(maxCacheableQuerySize: Int)(using system: ActorSystem):
	import system.dispatcher
	private val inner = LfuCache.apply[Sha256Sum, RouteResult]

	def apply(key: Sha256Sum, genValue: () => Future[RouteResult]): Future[RouteResult] =
		inner.get(key).getOrElse(genValue().map(makeCached(key)))

	def put(key: Sha256Sum, mayBeValue: Future[RouteResult]): Future[RouteResult] =
		mayBeValue.map(makeCached(key))

	def contains(key: Sha256Sum) = inner.keys.contains(key)

	def makeKey(reqCtxt: RequestContext): Option[Sha256Sum] = Some(reqCtxt).filter(shouldCache).map: reqCtxt =>
		val req = reqCtxt.request
		val accept = req.header[Accept].map(_.mediaRanges.map(_.value)).fold("")(_.sorted.mkString)
		val query = req.uri.rawQueryString.getOrElse("")
		val payload = req.entity match
			case Strict(_, data) => data
			//randomize cache key to prevent caching in other cases
			case _ => ByteString(Random.nextBytes(32))

		val digest = MessageDigest.getInstance("SHA-256")
		digest.update(accept.getBytes())
		digest.update(query.getBytes())
		payload.asByteBuffers.foreach(digest.update)
		Sha256Sum(digest.digest())

	private def shouldCache(ctxt: RequestContext): Boolean =
		val meth = ctxt.request.method
		meth == HttpMethods.GET || meth == HttpMethods.POST


	private def makeCached(key: Sha256Sum)(rr: RouteResult)(using ExecutionContext): RouteResult = rr match

		case _: Rejected => rr

		case Complete(response) =>

			def completeResult(ent: ResponseEntity): RouteResult =
				// Access-Control-Allow-Origin is specific for the web app that makes the query, so should not be cached
				Complete(response.withEntity(ent).mapHeaders(_.filterNot(_.name == `Access-Control-Allow-Origin`.name)))

			import response.status
			val shouldBeCached = status.isSuccess() && status != StatusCodes.NoContent

			if !shouldBeCached then rr else
				val cacheingEntity = response.entity match
					case se: Strict => se
					case ent =>
						val cacheingPayload = ent.dataBytes.alsoTo:
							cacheUpdatingSink: cache =>
								val builder = ByteString.newBuilder
								cache.foreach(builder.addAll)
								val entity = HttpEntity.Strict(ent.contentType, builder.result())
								val rRes = completeResult(entity)
								inner.put(key, Future.successful(rRes))

						HttpEntity.CloseDelimited(ent.contentType, cacheingPayload)

				val cachedRr = completeResult(cacheingEntity)

				cacheingEntity match
					case _: Strict => inner.put(key, Future.successful(cachedRr))
					case _ =>

				cachedRr
	end makeCached

	private type MaybeCache = Option[Seq[ByteString]]

	private def cacheUpdatingSink(callback: Seq[ByteString] => Unit): Sink[ByteString, NotUsed] =
		cacheBuildingSink.mapMaterializedValue: fut =>
			fut.foreach(_.foreach(callback))
			NotUsed

	private val cacheBuildingSink: Sink[ByteString, Future[MaybeCache]] =
		val aux = Sink.fold[Option[(Queue[ByteString], Int)], ByteString](Some(Queue.empty -> 0)): (acc, elem) =>
			acc.flatMap: (cache, cost) =>
				val newCost = cost + elem.length
				if newCost > maxCacheableQuerySize then None
				else Some((cache :+ elem, newCost))
		aux.mapMaterializedValue(_.map(_.map(_._1)))

end SparqlCache
