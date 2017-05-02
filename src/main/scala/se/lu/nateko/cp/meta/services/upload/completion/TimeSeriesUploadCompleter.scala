package se.lu.nateko.cp.meta.services.upload.completion

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import se.lu.nateko.cp.meta.api.EpicPidClient
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.TimeSeriesUploadCompletion
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UploadCompletionException
import se.lu.nateko.cp.meta.services.upload.MetadataUpdater


private class TimeSeriesUploadCompleter(
	server: InstanceServer,
	epic: EpicPidClient,
	vocab: CpVocab,
	metaVocab: CpmetaVocab
)(implicit ex: ExecutionContext) extends EpicPidMinter(epic, vocab) {

	private val factory = vocab.factory

	override def getUpdates(hash: Sha256Sum, info: UploadCompletionInfo): Future[Seq[RdfUpdate]] = info match {

		case TimeSeriesUploadCompletion(interval) => Future{
			val acqUri = vocab.getAcquisition(hash)

			val news = Seq(
				factory.createStatement(acqUri, metaVocab.prov.startedAtTime, vocab.lit(interval.start)),
				factory.createStatement(acqUri, metaVocab.prov.endedAtTime, vocab.lit(interval.stop))
			)

			val olds = (
				server.getStatements(Some(acqUri), Some(metaVocab.prov.startedAtTime), None) ++
				server.getStatements(Some(acqUri), Some(metaVocab.prov.endedAtTime), None)
			).toIndexedSeq

			MetadataUpdater.diff(olds, news)
		}

		case _ => Future.failed(new UploadCompletionException(
			s"Encountered wrong type of upload completion info, must be TimeSeriesUploadCompletion, got $info"
		))
	}

}
