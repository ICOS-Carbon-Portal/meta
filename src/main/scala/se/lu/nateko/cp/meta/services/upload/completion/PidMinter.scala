package se.lu.nateko.cp.meta.services.upload.completion

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.services.CpVocab

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class PidMinter(handles: HandleNetClient, vocab: CpVocab)(using Envri) extends FormatSpecificCompleter:

	override def getUpdates(hash: Sha256Sum)(using TriplestoreConnection): Seq[RdfUpdate] = Nil

	final def finalize(hash: Sha256Sum): Future[Report] =

		val targetUri = vocab.getStaticObject(hash)
		val suffix = handles.pidFactory.getSuffix(hash)

		handles.createOrRecreate(suffix, new java.net.URI(targetUri.stringValue))
			.map(_ => new Report(handles.pidFactory.getPid(suffix)))(using ExecutionContext.parasitic)
