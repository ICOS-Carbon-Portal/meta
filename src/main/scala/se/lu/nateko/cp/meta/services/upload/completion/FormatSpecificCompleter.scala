package se.lu.nateko.cp.meta.services.upload.completion

import scala.concurrent.Future

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

case class Report(message: String)

trait FormatSpecificCompleter {

	def getUpdates(hash: Sha256Sum, info: UploadCompletionInfo): Future[Seq[RdfUpdate]]

	def finalize(hash: Sha256Sum): Future[Report]
}
