package se.lu.nateko.cp.meta.utils.streams

import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import scala.collection.mutable.Queue

import ZipEntryFlow.ZipEntrySegment
import ZipEntryFlow.ZipEntryStart
import ZipEntryFlow.ZipFlowElement
import akka.NotUsed
import akka.stream.Attributes
import akka.stream.FlowShape
import akka.stream.Inlet
import akka.stream.Outlet
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import akka.stream.stage.GraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.InHandler
import akka.stream.stage.OutHandler
import akka.util.ByteString

object ZipEntryFlow {

	sealed trait ZipFlowElement
	case class ZipEntryStart(fileName: String) extends ZipFlowElement
	case class ZipEntrySegment(bytes: ByteString) extends ZipFlowElement

	type FileLikeSource = Source[ByteString, Any]

	def getMultiEntryZipStream(entries: Seq[(String, FileLikeSource)]) : Source[ByteString, NotUsed] = {
		val zipFlowSources = entries.map{
			case (fileName, fileSource) =>
				val headerSource: Source[ZipFlowElement, NotUsed] = Source.single(new ZipEntryStart(fileName))
				val bodySource: Source[ZipFlowElement, NotUsed] =
					fileSource.map(bs => new ZipEntrySegment(bs)).mapMaterializedValue(_ => NotUsed)
				headerSource.concat(bodySource)
		}
		val concatenated = zipFlowSources.foldLeft(Source.empty[ZipFlowElement])(_ concat _)
		concatenated.via(Flow.fromGraph(new ZipEntryFlow))
	}

}


private class ZipEntryFlow extends GraphStage[FlowShape[ZipFlowElement, ByteString]]{

	private val in: Inlet[ZipFlowElement] = Inlet("ZipEntryFlowInput")
	private val out: Outlet[ByteString] = Outlet("ZipEntryFlowOutput")

	override val shape = FlowShape(in, out)

	override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape){

		private val bsq = Queue.empty[ByteString]
		private val os = new java.io.OutputStream{
			override def write(b: Array[Byte]) = bsq += ByteString(b)
			override def write(b: Array[Byte], off: Int, len: Int) = bsq += ByteString.fromArray(b, off, len)
			override def write(b: Int) = bsq += ByteString(b.toByte)
			
		}
		private val zos = new ZipOutputStream(new BufferedOutputStream(os))

		override def preStart(): Unit = pull(in) //initial pull

		setHandler(in, new InHandler{
			override def onPush(): Unit = {
				grabAndZip()
				if(isAvailable(out)) pushResultOut()
			}

			override def onUpstreamFinish(): Unit = {
//				println("onUpstreamFinish ...")
				if(isAvailable(in)) grabAndZip()
//				println("Closing stream ...")
				zos.close()
//				println("Closed stream!")
				if(isAvailable(out)) pushResultOut()
			}
		})

		setHandler(out, new OutHandler {
			override def onPull(): Unit = {
				pushResultOut()
			}
		})

		private def pushResultOut(): Unit = {
			if(bsq.isEmpty){
//				println("No zipped result available yet ...")
				if(!hasBeenPulled(in) && !isClosed(in)) {
//					println("Pulling ...")
					pull(in)
				}else if(isClosed(in) && !hasBeenPulled(in) && !isAvailable(in)){
					completeStage()
				}else{
//					println("Doing nothing ...")
				}
			}
			else{
				val bs = bsq.dequeue()
				push(out, bs)
//				println("Pushed " + bs.length + " bytes out")
			}
		}

		private def grabAndZip(): Unit = grab(in) match {

			case ZipEntryStart(fileName) =>
				zos.putNextEntry(new ZipEntry(fileName))

			case ZipEntrySegment(bytes) =>
				val arr = Array.ofDim[Byte](bytes.size)
				bytes.copyToArray(arr)
//				println("Will zip " + arr.length + " bytes")
				zos.write(arr)
//				println("Zipped " + arr.length + " bytes")

		}
	}
}
