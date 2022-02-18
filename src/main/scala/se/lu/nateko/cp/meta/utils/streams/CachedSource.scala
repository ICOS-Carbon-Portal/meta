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

object CachedSource {

	def apply[T](inner: Source[T, Any])(
		implicit mat: Materializer, exe: ExecutionContext
	): Source[T, NotUsed] = {

		val sinkQueue = inner.toMat(Sink.queue[T]())(Keep.right).run()
		val cache = Buffer.empty[Future[Option[T]]]

		Source.unfoldResourceAsync[T, SinkQueueWithCancel[T]](
			() => Future.successful(new CachingSinkQueue[T](sinkQueue, cache)),
			_.pull(),
			_ => Future.successful(Done)
		)
	}
}

private class CachingSinkQueue[T](
	inner: SinkQueueWithCancel[T], cache: Buffer[Future[Option[T]]]
)(implicit exe: ExecutionContext) extends SinkQueueWithCancel[T]{

	//private[this] var canceled: Boolean = false
	//private[this] var completed: Boolean = false
	private[this] var cursor: Int = 0

	private def lastCache: Future[Option[T]] =
		if(cursor > 0 && cursor - 1 < cache.length) cache(cursor - 1) else Future.successful(None)

	override def pull(): Future[Option[T]] = cache.synchronized{
	//	if(completed || canceled) lastCache
		if(cursor < cache.length){
			cursor += 1
			cache(cursor - 1)
		}
		else lastCache.flatMap{_ =>
			cache.synchronized{
				cache += inner.pull()
				// .transform(innerRes => cache.synchronized{
				// 	innerRes match{
				// 		case Failure(_) => completed = true
				// 		case Success(None) => completed = true
				// 		case _ =>
				// 	}
				// 	innerRes
				// })
				cursor += 1
				cache(cursor - 1)
			}
		}
	}

	override def cancel(): Unit = {}
	// cache.synchronized{
	// 	canceled = true
	// }

}
