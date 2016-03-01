package se.lu.nateko.cp.meta.services.upload

import java.time.Instant

import scala.concurrent.Future
import scala.util.Try

import org.openrdf.model.URI

import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.EpicPidConfig
import se.lu.nateko.cp.meta.api.EpicPidClient
import se.lu.nateko.cp.meta.api.PidUpdate
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpmetaVocab
import spray.json.JsString

sealed trait UploadCompletionInfo
case object EmptyCompletionInfo extends UploadCompletionInfo

class UploadCompleter(server: InstanceServer, conf: EpicPidConfig, vocab: CpmetaVocab)(implicit system: ActorSystem) {
	import system.dispatcher

	private val epic = EpicPidClient(conf)

	def completeUpload(hash: Sha256Sum, info: UploadCompletionInfo): Future[String] = {
		for(
			_ <- uploadIsNotCompleteYet(hash);
			format <- getObjectSpecificationFormat(hash);
			result <- completeUpload(hash, format, info)
		) yield result
	}

	def getPid(hash: Sha256Sum): String = epic.getPid(getPidSuffix(hash))

	private def getPidSuffix(hash: Sha256Sum): String = hash.id

	private def getObjectSpecificationFormat(hash: Sha256Sum): Future[URI] = {
		import InstanceServer.ExactlyOne
		val dataObjUri = vocab.getDataObject(hash)

		Future.fromTry(Try{
			val objSpec = server.getUriValues(dataObjUri, vocab.hasObjectSpec, ExactlyOne).head
			server.getUriValues(objSpec, vocab.hasFormat, ExactlyOne).head
		})
	}

	private def completeUpload(hash: Sha256Sum, format: URI, info: UploadCompletionInfo): Future[String] = {
		if(format == vocab.wdcggFormat){
			writeUploadStopTime(hash).map(_ => vocab.getDataObject(hash).stringValue)
		}else for(
			pid <- mintEpicPid(hash);
			_ <- writeUploadStopTime(hash)
		) yield pid
	}

	private def mintEpicPid(hash: Sha256Sum): Future[String] = {
		val targetUri = vocab.getDataObject(hash)
		val pidEntry = PidUpdate("URL", JsString(targetUri.toString))
		epic.create(getPidSuffix(hash), Seq(pidEntry))
			.map(_ => getPid(hash))
	}

	private def writeUploadStopTime(hash: Sha256Sum): Future[Unit] = {
		val submissionUri = vocab.getSubmission(hash)
		val stopInfo = vocab.factory.createStatement(submissionUri, vocab.prov.endedAtTime, vocab.lit(Instant.now))
		Future.fromTry(server.add(stopInfo))
	}

	private def uploadIsNotCompleteYet(hash: Sha256Sum): Future[Unit] = {
		val submissionUri = vocab.getSubmission(hash)
		if(server.getValues(submissionUri, vocab.prov.endedAtTime).isEmpty)
			Future.successful(())
		else
			Future.failed(new Exception(s"Upload of $hash is already complete"))
	}
}