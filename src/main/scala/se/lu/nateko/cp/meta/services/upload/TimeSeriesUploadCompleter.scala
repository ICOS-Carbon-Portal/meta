package se.lu.nateko.cp.meta.services.upload

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.openrdf.model.URI
import org.openrdf.model.Value
import org.openrdf.model.vocabulary.OWL
import org.openrdf.model.vocabulary.RDF
import org.openrdf.model.vocabulary.RDFS

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.core.data.TimeSeriesUploadCompletion
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.instanceserver.InstanceServer

import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UploadCompletionException
import se.lu.nateko.cp.meta.utils.sesame.EnrichedValueFactory
import scala.util.Try


private class TimeSeriesUploadCompleter(
	val server: InstanceServer,
	vocab: CpVocab,
	metaVocab: CpmetaVocab
)(implicit ctxt: ExecutionContext) {

	private val factory = vocab.factory

	def writeMetadata(hash: Sha256Sum, info: UploadCompletionInfo): Future[Unit] = info match {

		case TimeSeriesUploadCompletion(interval) => Future{

			val objUri = vocab.getDataObject(hash)
			val acquisitionUri = vocab.getAcquisition(hash)

			val facts = (acquisitionUri, metaVocab.prov.startedAtTime, vocab.lit(interval.start)) +:
				(acquisitionUri, metaVocab.prov.endedAtTime, vocab.lit(interval.stop)) +: Nil

			server.addAll(facts.map(factory.tripleToStatement))
		}

		case _ => Future.failed(new UploadCompletionException(
			s"Encountered wrong type of upload completion info, must be TimeSeriesUploadCompletion, got $info"
		))
	}

}
