package se.lu.nateko.cp.meta.utils.streams

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.Done
import akka.stream.Attributes
import akka.stream.Outlet
import akka.stream.SourceShape
import akka.stream.scaladsl.Source
import akka.stream.stage.AsyncCallback
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.GraphStageWithMaterializedValue
import akka.stream.stage.OutHandler
import akka.util.ByteString

object OutputStreamWriterSource {

	def apply(writer: OutputStream => Unit)(implicit ctxt: ExecutionContext) : Source[ByteString, Future[Done]] =
		Source.fromGraph(new OutputStreamWriterSource(writer))
}

private class OutputStreamWriterSource(writer: OutputStream => Unit)(implicit ctxt: ExecutionContext) extends
		GraphStageWithMaterializedValue[SourceShape[ByteString], Future[Done]]{

	private val out: Outlet[ByteString] = Outlet("OutputStreamWriterSourceOutput")

	override val shape = SourceShape(out)

	override def createLogicAndMaterializedValue(inheritedAttributes: Attributes) = {
		val logic = new GraphStageLogic(shape){

			private[this] val done = Promise[Done]()
			private[this] val bsq = new ArrayBlockingQueue[ByteString](3)

			val materializedValue = done.future

			override def preStart(): Unit = {

				val asyncPush = getAsyncCallback[Unit]{_ =>
					if(isAvailable(out)) {
						push(out, bsq.take())
					}
				}

				val completer = getAsyncCallback[Try[Done]]{
					case Success(_) =>
						if(isAvailable(out)) {
							pushIfAny()
							if(bsq.peek == null) completeStage()
						}
					case Failure(err) => failStage(err)
				}

				Future{
					val os = new QueueingAndSyncingOutputStream(bsq, () => asyncPush.invoke(()))
					val bos = new BufferedOutputStream(os)
					writer(os)
					Done
				}.onComplete{result => try{
						completer.invoke(result)
						done.complete(result)
					}catch{
						// in the unlikely case completer.invoke threw an exception
						case err: Throwable => done.failure(err)
					}
				}
			}

			setHandler(out, new OutHandler {
				override def onPull(): Unit = pushIfAny()
			})

			private def pushIfAny(): Unit = {
				val head = bsq.poll()
				if(head != null) {
					push(out, head)
				} else if(done.isCompleted) completeStage() //queue is empty and writer finished
			}
		}

		(logic, logic.materializedValue)
	}
}

private class QueueingAndSyncingOutputStream(
	bsq: ArrayBlockingQueue[ByteString],
	sync: () => Unit
) extends OutputStream{

	override def write(b: Array[Byte]) = {
		bsq.put(ByteString(b))
		sync()
	}
	override def write(b: Array[Byte], off: Int, len: Int) = {
		bsq.put(ByteString.fromArray(b, off, len))
		sync()
	}
	override def write(b: Int) = {
		bsq.put(ByteString(b.toByte))
		sync()
	}
}
