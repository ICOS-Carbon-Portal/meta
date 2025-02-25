package se.lu.nateko.cp.meta.services.upload.completion

import scala.concurrent.Future
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.{RdfUpdate, TriplestoreConnection}

class Report(val message: String)

trait FormatSpecificCompleter:

	def getUpdates(hash: Sha256Sum)(using TriplestoreConnection): Seq[RdfUpdate]

	def finalize(hash: Sha256Sum): Future[Report]
