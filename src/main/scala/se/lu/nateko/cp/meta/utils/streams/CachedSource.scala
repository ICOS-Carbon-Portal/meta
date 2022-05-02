package se.lu.nateko.cp.meta.utils.streams

import akka.Done
import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.SinkQueueWithCancel
import akka.stream.scaladsl.Source
import akka.util.ByteString
import se.lu.nateko.cp.meta.services.CacheSizeLimitExceeded
import se.lu.nateko.cp.meta.services.ServiceException

import scala.collection.mutable.Buffer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

object CachedSource {

	class Quota[T](val cost: T => Int, val maxCost: Int)

	def apply[T](inner: Source[T, Any], quota: Quota[T])(
		implicit mat: Materializer, exe: ExecutionContext
	): Source[T, NotUsed] = {
		val sinkQueue = inner.toMat(Sink.queue[T]())(Keep.right).run()
		val cache = Buffer.empty[Future[Option[T]]]

		Source.unfoldResourceAsync[T, SinkQueueWithCancel[T]](
			() => Future.successful(new CachingSinkQueue[T](sinkQueue, cache, quota)),
			_.pull(),
			_ => Future.successful(Done)
		)
	}
}

private class CachingSinkQueue[T](
	inner: SinkQueueWithCancel[T], cache: Buffer[Future[Option[T]]], quota: CachedSource.Quota[T]
)(implicit exe: ExecutionContext) extends SinkQueueWithCancel[T]{

	private var cursor: Int = 0
	private var cost: Int = 0

	override def pull(): Future[Option[T]] = cache.synchronized{
		if(cursor >= cache.length) innerPull()
		cursor += 1
		cache(cursor - 1)
	}

	//don't cancel the inner, because another request may want to have it all
	override def cancel(): Unit = {}

	private def innerPull(): Unit = cache +=
		cache.lastOption.fold(innerInnerPull()){
			_.flatMap{
				case Some(_) => innerInnerPull()
				case None => Future.successful(None)
			}
		}

	private def innerInnerPull(): Future[Option[T]] = cache.synchronized{
		if(cost < quota.maxCost)
			inner.pull().andThen{
				//make sure to eagerly consume the inner queue
				case Success(Some(elem)) => cache.synchronized{
					cost += quota.cost(elem)
					innerPull()
				}
			}
		else{
			inner.cancel()
			Future.failed(CacheSizeLimitExceeded)
		}
	}
}
