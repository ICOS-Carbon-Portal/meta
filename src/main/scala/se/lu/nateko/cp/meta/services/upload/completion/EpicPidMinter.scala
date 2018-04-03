package se.lu.nateko.cp.meta.services.upload.completion

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import se.lu.nateko.cp.meta.api.EpicPidClient
import se.lu.nateko.cp.meta.api.PidUpdate
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpVocab
import spray.json.JsString
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import java.util.concurrent.TimeoutException
import se.lu.nateko.cp.meta.services.PidMintingException
import se.lu.nateko.cp.meta.core.data.Envri.Envri

class EpicPidMinter(epic: EpicPidClient, vocab: CpVocab)(implicit ex: ExecutionContext, envri: Envri) extends FormatSpecificCompleter {

	def getUpdates(hash: Sha256Sum): Future[Seq[RdfUpdate]] =
		Future.successful(Nil)


	final def finalize(hash: Sha256Sum): Future[Report] = {

		val targetUri = vocab.getDataObject(hash)
		val suffix = epic.getSuffix(hash)
		val pidEntry = PidUpdate("URL", JsString(targetUri.toString))

		val reportFut = epic.createOrRecreate(suffix, Seq(pidEntry))
			.map(_ => Report(epic.getPid(suffix)))

		Await.ready(reportFut, 6.seconds).recoverWith{
			case _: TimeoutException =>
				Future.failed(new PidMintingException("PID minting timed out"))
		}
	}
}