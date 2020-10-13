package se.lu.nateko.cp.meta.upload

import java.security.MessageDigest
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Success
import akka.stream.Attributes
import akka.stream.FlowShape
import akka.stream.Inlet
import akka.stream.Outlet
import akka.stream.scaladsl.Flow
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.GraphStageWithMaterializedValue
import akka.stream.stage.InHandler
import akka.stream.stage.OutHandler
import akka.util.ByteString
import se.lu.nateko.cp.meta.core.crypto.Md5Sum
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import scala.util.Failure

object DigestFlow{

	def md5 = forDigest("MD5")(bytes => new Md5Sum(bytes))
	def sha1 = forDigest("SHA-1")(identity)
	def sha256 = forDigest("SHA-256")(bytes => new Sha256Sum(bytes))

	private def forDigest[T](digestName: String)(map: Array[Byte] => T): Flow[ByteString, ByteString, Future[T]] =
		Flow.fromGraph(new DigestFlow(digestName, map))
}

private class DigestFlow[T](digest: String, map: Array[Byte] => T) extends GraphStageWithMaterializedValue[FlowShape[ByteString, ByteString], Future[T]]{

	private[this] val in: Inlet[ByteString] = Inlet("DigestComputerInput")
	private[this] val out: Outlet[ByteString] = Outlet("DigestComputerOutput")

	override val shape = FlowShape(in, out)

	override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[T]) = {

		val matValPromise = Promise[T]()

		val logic = new GraphStageLogic(shape){

			private val md = MessageDigest.getInstance(digest)
			private def completeDigest(): Unit = matValPromise.complete(Success(map(md.digest)))

			setHandler(in, new InHandler{
				override def onPush(): Unit = {
					val bs = grab(in)
					push(out, bs)
					bs.asByteBuffers.foreach(md.update)
				}

				override def onUpstreamFinish(): Unit = {
					completeDigest()
					super.onUpstreamFinish()
				}

				override def onUpstreamFailure(ex: Throwable): Unit = {
					matValPromise.complete(Failure(ex))
					super.onUpstreamFailure(ex)
				}
			})

			setHandler(out, new OutHandler {
				override def onPull(): Unit = pull(in)

				override def onDownstreamFinish(cause: Throwable): Unit = {
					completeDigest()
					super.onDownstreamFinish(cause)
				}
			})
		}

		(logic, matValPromise.future)
	}
}
