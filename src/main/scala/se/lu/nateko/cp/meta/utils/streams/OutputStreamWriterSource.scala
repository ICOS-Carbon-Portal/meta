package se.lu.nateko.cp.meta.utils.streams

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise

import akka.Done
import akka.stream.Attributes
import akka.stream.Outlet
import akka.stream.SourceShape
import akka.stream.scaladsl.Source
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
		val done = Promise[Done]()
		val bsq = new ArrayBlockingQueue[ByteString](3)

		val logic = new GraphStageLogic(shape){

			val asyncPush = getAsyncCallback[Unit]{_ =>
				if(isAvailable(out)) {
					val bs = bsq.take()
					push(out, bs)
//					println(s"pushed ${bs.length} bytes out!")
				}// else println("output blocked!")
			}
			val asyncClose = getAsyncCallback[Unit]{_ =>
				if(isAvailable(out)) {
					pushIfAny()
					if(bsq.peek == null) completeStage()
				}
			}

			setHandler(out, new OutHandler {
				override def onPull(): Unit = pushIfAny()
			})

			private def pushIfAny(): Unit = {
				val head = bsq.poll()
				if(head != null) {
					push(out, head)
//					println(s"${head.length} bytes pulled out")
				}
				else {
//					println(s"queue was empty, nothing to pull out")
					if(done.isCompleted) completeStage() //queue is empty and writing is done
				}
			}
		}

		val os = new OutputStream{
			override def write(b: Array[Byte]) = {
				bsq.put(ByteString(b))
				logic.asyncPush.invoke(())
			}
			override def write(b: Array[Byte], off: Int, len: Int) = {
//				println("got an array")
				bsq.put(ByteString.fromArray(b, off, len))
				logic.asyncPush.invoke(())
			}
			override def write(b: Int) = {
				bsq.put(ByteString(b.toByte))
				logic.asyncPush.invoke(())
			}
			override def close(): Unit = logic.asyncClose.invoke(())
		}
		Future{
			try{
				writer(new BufferedOutputStream(os))
//				println("reporting success!")
				done.success(Done)
			}catch{
				case err: Throwable =>
//					println("failing!")
					done.failure(err)
			}
		}
		(logic, done.future)
	}
}
