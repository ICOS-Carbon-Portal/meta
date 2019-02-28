package se.lu.nateko.cp.meta.services.upload.completion

import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.PidMintingException

class PidMinter(handles: HandleNetClient, vocab: CpVocab)(implicit ex: ExecutionContext, envri: Envri) extends FormatSpecificCompleter {

	def getUpdates(hash: Sha256Sum): Future[Seq[RdfUpdate]] =
		Future.successful(Nil)


	final def finalize(hash: Sha256Sum): Future[Report] = {

		val targetUri = vocab.getStaticObject(hash)
		val suffix = handles.pidFactory.getSuffix(hash)

		handles.createOrRecreate(suffix, new java.net.URL(targetUri.stringValue))
			.map(_ => Report(handles.pidFactory.getPid(suffix)))

	}
}