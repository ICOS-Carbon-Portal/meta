package se.lu.nateko.cp.meta.services.upload.completion

import scala.concurrent.Future

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

class Report(val message: String)

trait FormatSpecificCompleter {

	def getUpdates(hash: Sha256Sum): Future[Seq[RdfUpdate]]

	def finalize(hash: Sha256Sum): Future[Report]
}
