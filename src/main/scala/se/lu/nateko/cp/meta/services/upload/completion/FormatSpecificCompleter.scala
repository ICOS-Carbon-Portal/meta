package se.lu.nateko.cp.meta.services.upload.completion

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection

import scala.concurrent.Future

class Report(val message: String)

trait FormatSpecificCompleter:

	def getUpdates(hash: Sha256Sum)(using TriplestoreConnection): Seq[RdfUpdate]

	def finalize(hash: Sha256Sum): Future[Report]
