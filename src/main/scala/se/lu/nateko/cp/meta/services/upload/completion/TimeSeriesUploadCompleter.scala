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
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.services.upload.StatementsProducer
import se.lu.nateko.cp.meta.core.data.SpatialTimeSeriesUploadCompletion
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
import org.eclipse.rdf4j.model.IRI


private class TimeSeriesUploadCompleter(
	server: InstanceServer,
	epic: EpicPidClient,
	vocab: CpVocab,
	metaVocab: CpmetaVocab
)(implicit ex: ExecutionContext) extends EpicPidMinter(epic, vocab) {

	private val factory = vocab.factory
	private val statementsProd = new StatementsProducer(vocab, metaVocab)

	override def getUpdates(hash: Sha256Sum, info: UploadCompletionInfo): Future[Seq[RdfUpdate]] = info.ingestionResult match {

		case Some(TimeSeriesUploadCompletion(interval)) => Future{
			acqusitionIntervalUpdates(hash, interval)
		}

		case Some(SpatialTimeSeriesUploadCompletion(interval, spatial)) => Future{
			val news = statementsProd.getGeoFeatureStatements(hash, spatial)

			val objUri = vocab.getDataObject(hash)
			val oldCovs = server.getStatements(Some(objUri), Some(metaVocab.hasSpatialCoverage), None).toIndexedSeq

			val olds = oldCovs ++ oldCovs.collect{
				case Rdf4jStatement(_, _, coverage: IRI) =>
					server.getStatements(Some(coverage), None, None)
			}.flatten

			val coverageUpdates = MetadataUpdater.diff(olds, news, factory)
			val intervalUpdates = acqusitionIntervalUpdates(hash, interval)

			coverageUpdates ++ intervalUpdates
		}

		case None => Future.successful(Nil)

		case _ => Future.failed(new UploadCompletionException(
			s"Encountered wrong type of upload completion info, must be (Spatial)TimeSeriesUploadCompletion, got $info"
		))
	}

	private def acqusitionIntervalUpdates(hash: Sha256Sum, interval: TimeInterval): Seq[RdfUpdate] = {
		val acqUri = vocab.getAcquisition(hash)

		val news = IndexedSeq(
			factory.createStatement(acqUri, metaVocab.prov.startedAtTime, vocab.lit(interval.start)),
			factory.createStatement(acqUri, metaVocab.prov.endedAtTime, vocab.lit(interval.stop))
		)

		val olds = server.getStatements(Some(acqUri), Some(metaVocab.prov.startedAtTime), None).toIndexedSeq ++
			server.getStatements(Some(acqUri), Some(metaVocab.prov.endedAtTime), None)

		MetadataUpdater.diff(olds, news, factory)
	}
}
