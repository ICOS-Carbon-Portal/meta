package se.lu.nateko.cp.meta.services.upload

import java.time.Instant

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.openrdf.model.URI

import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.api.EpicPidClient
import se.lu.nateko.cp.meta.api.PidUpdate
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.UploadCompletionException
import spray.json.JsString

class UploadCompleter(servers: DataObjectInstanceServers, conf: UploadServiceConfig)(implicit system: ActorSystem) {
	import system.dispatcher

	private val epic = EpicPidClient(conf.epicPid)
	import servers.{ metaVocab, vocab }

	def getPid(hash: Sha256Sum): String = epic.getPid(getPidSuffix(hash))

	def completeUpload(hash: Sha256Sum, info: UploadCompletionInfo): Future[String] = {
		for(
			(format, server) <- Future.fromTry(getFormatAndServer(hash));
			result <- completeUpload(server, hash, format, info)
		) yield result
	}

	private def getPidSuffix(hash: Sha256Sum): String = hash.id

	private def getFormatAndServer(hash: Sha256Sum): Try[(URI, InstanceServer)] =
		for(
			objSpec <- servers.getDataObjSpecification(hash);
			format <- servers.getObjSpecificationFormat(objSpec);
			server <- servers.getInstServerForFormat(format);
			_ <- uploadIsNotCompleteYet(hash, server)
		) yield (format, server)

	private def uploadIsNotCompleteYet(hash: Sha256Sum, server: InstanceServer): Try[Unit] = {
		val submissionUri = vocab.getSubmission(hash)
		if(server.getValues(submissionUri, metaVocab.prov.endedAtTime).isEmpty)
			Success(())
		else
			Failure(new UploadCompletionException(s"Upload of $hash is already complete"))
	}

	private def completeUpload(server: InstanceServer, hash: Sha256Sum, format: URI, info: UploadCompletionInfo): Future[String] = {

		if(format == metaVocab.wdcggFormat){
			val wdcggCompleter = new WdcggUploadCompleter(server, vocab, metaVocab)
			for(
				_ <- wdcggCompleter.writeMetadata(hash, info);
				_ <- writeUploadStopTime(server, hash)
			) yield vocab.getDataObject(hash).stringValue

		} else if(format == metaVocab.etcFormat || format == metaVocab.socatFormat){
			val completer = new TimeSeriesUploadCompleter(server, vocab, metaVocab)
			for(
				_ <- completer.writeMetadata(hash, info);
				_ <- writeUploadStopTime(server, hash);
				pid <- mintEpicPid(hash)
			) yield pid

		} else for(
			pid <- mintEpicPid(hash);
			_ <- writeUploadStopTime(server, hash)
		) yield pid
	}

	private def mintEpicPid(hash: Sha256Sum): Future[String] = {
		val targetUri = vocab.getDataObject(hash)
		val pidEntry = PidUpdate("URL", JsString(targetUri.toString))
		epic.createOrRecreate(getPidSuffix(hash), Seq(pidEntry))
			.map(_ => getPid(hash))
	}

	private def writeUploadStopTime(server: InstanceServer, hash: Sha256Sum): Future[Unit] = {
		val submissionUri = vocab.getSubmission(hash)
		val stopInfo = vocab.factory.createStatement(submissionUri, metaVocab.prov.endedAtTime, vocab.lit(Instant.now))
		Future.fromTry(server.add(stopInfo))
	}

}
