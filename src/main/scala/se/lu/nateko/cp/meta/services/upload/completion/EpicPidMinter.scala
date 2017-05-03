package se.lu.nateko.cp.meta.services.upload.completion

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import se.lu.nateko.cp.meta.api.EpicPidClient
import se.lu.nateko.cp.meta.api.PidUpdate
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpVocab
import spray.json.JsString

class EpicPidMinter(epic: EpicPidClient, vocab: CpVocab)(implicit ex: ExecutionContext) extends FormatSpecificCompleter {

	def getUpdates(hash: Sha256Sum, info: UploadCompletionInfo): Future[Seq[RdfUpdate]] =
		Future.successful(Nil)


	final def finalize(hash: Sha256Sum): Future[Report] = {

		val targetUri = vocab.getDataObject(hash)
		val suffix = epic.getSuffix(hash)
		val pidEntry = PidUpdate("URL", JsString(targetUri.toString))

		epic.createOrRecreate(suffix, Seq(pidEntry))
			.map(_ => Report(epic.getPid(suffix)))
	}
}