package se.lu.nateko.cp.meta.utils.streams

import akka.stream.scaladsl.Source
import akka.NotUsed
import akka.stream.Materializer
import scala.concurrent.ExecutionContext
import akka.util.ByteString
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.SinkQueueWithCancel
import scala.concurrent.Future
import scala.collection.mutable.Buffer
import scala.util.Failure
import scala.util.Success
import akka.Done
import se.lu.nateko.cp.meta.services.ServiceException

object CachedSource {

	class Quota[T](
		val cost: T => Int,
		val maxCost: Long,
		val cancelAction: () => Unit,
		val lastElemIfExceeded: T
	)

	def apply[T](inner: Source[T, Any], quota: Quota[T])(
		implicit mat: Materializer, exe: ExecutionContext
	): Source[T, NotUsed] = {
		println("MAKING NEW CACHEDSOURCE")
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

	private[this] var cursor: Int = 0
	private[this] var cost: Long = 0L
	private[this] var quotaExceeded: Boolean = false

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
		else if(quotaExceeded)
			Future.successful(None)
		else{
			quotaExceeded = true
			println("CANCELLING INNER")
			inner.cancel()
			quota.cancelAction()
			Future.successful(Some(quota.lastElemIfExceeded))
		}
	}
}
