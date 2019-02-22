package se.lu.nateko.cp.meta.services.upload.completion

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UploadCompletionException
import se.lu.nateko.cp.meta.services.upload.MetadataUpdater
import se.lu.nateko.cp.meta.services.upload.StatementsProducer
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
import spray.json.{JsArray, JsString}


private class TimeSeriesUploadCompleter(
	server: InstanceServer,
	extract: IngestionMetadataExtract,
	handles: HandleNetClient,
	vocab: CpVocab,
	metaVocab: CpmetaVocab
)(implicit ex: ExecutionContext, envri: Envri) extends PidMinter(handles, vocab) {

	private val factory = vocab.factory
	private val statementsProd = new StatementsProducer(vocab, metaVocab)

	override def getUpdates(hash: Sha256Sum): Future[Seq[RdfUpdate]] = extract match {

		case TimeSeriesUploadCompletion(ingestionExtract, rowsInfo) => Future{
			tabularExtractUpdates(hash, ingestionExtract) ++ nRowsUpdates(hash, rowsInfo)
		}

		case SpatialTimeSeriesUploadCompletion(ingestionExtract, spatial) => Future{
			val news = statementsProd.getGeoFeatureStatements(hash, spatial)

			val objUri = vocab.getDataObject(hash)
			val oldCovs = server.getStatements(Some(objUri), Some(metaVocab.hasSpatialCoverage), None).toIndexedSeq

			val olds = oldCovs ++ oldCovs.collect{
				case Rdf4jStatement(_, _, coverage: IRI) =>
					server.getStatements(Some(coverage), None, None)
			}.flatten

			val coverageUpdates = MetadataUpdater.diff(olds, news, factory)
			val intervalUpdates = tabularExtractUpdates(hash, ingestionExtract)

			coverageUpdates ++ intervalUpdates
		}

		case _ => Future.failed(new UploadCompletionException(
			s"Encountered wrong type of upload completion info, must be (Spatial)TimeSeriesUploadCompletion, got $extract"
		))
	}

	private def tabularExtractUpdates(hash: Sha256Sum, extract: TabularIngestionExtract): Seq[RdfUpdate] = {
		val objUri = vocab.getDataObject(hash)

		val olds = server.getStatements(Some(objUri), Some(metaVocab.hasActualColumnNames), None).toIndexedSeq

		val news = extract.actualColumns.toIndexedSeq.map { cols =>
			val json = JsArray(cols.map(cn => JsString(cn)).toVector)
			factory.createStatement(objUri, metaVocab.hasActualColumnNames, vocab.lit(json.compactPrint))
		}
		MetadataUpdater.diff(olds, news, factory) ++ acqusitionIntervalUpdates(hash, extract.interval)
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

	private def nRowsUpdates(hash: Sha256Sum, rowsInfo: Option[Int]): Seq[RdfUpdate] = rowsInfo match {
		case None => Nil
		case Some(nRows) =>
			val objUri = vocab.getDataObject(hash)
			val news = Seq(factory.createStatement(objUri, metaVocab.hasNumberOfRows, vocab.lit(nRows)))
			val olds = server.getStatements(Some(objUri), Some(metaVocab.hasNumberOfRows), None).toIndexedSeq
			MetadataUpdater.diff(olds, news, factory)
	}
}
