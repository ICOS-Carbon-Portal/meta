package se.lu.nateko.cp.meta.services.upload.completion

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UploadCompletionException
import se.lu.nateko.cp.meta.services.upload.MetadataUpdater
import se.lu.nateko.cp.meta.services.upload.StatementsProducer
import se.lu.nateko.cp.meta.utils.printAsJsonArray
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement


private class TimeSeriesUploadCompleter(
	extract: TimeSeriesExtract | SpatialTimeSeriesExtract,
	handles: HandleNetClient,
	vocab: CpVocab,
	metaVocab: CpmetaVocab
)(using Envri) extends PidMinter(handles, vocab):

	private val factory = vocab.factory
	import TriplestoreConnection.{TSC2, getStatements}

	override def getUpdates(hash: Sha256Sum)(using TriplestoreConnection): Seq[RdfUpdate] = extract match

		case TimeSeriesExtract(ingestionExtract, rowsInfo) =>
			tabularExtractUpdates(hash, ingestionExtract) ++ nRowsUpdates(hash, rowsInfo)

		case SpatialTimeSeriesExtract(ingestionExtract, spatial) =>
			val news = StatementsProducer(vocab, metaVocab).getGeoFeatureStatements(hash, spatial)

			val objUri = vocab.getStaticObject(hash)
			val oldCovs = getStatements(Some(objUri), Some(metaVocab.hasSpatialCoverage), None).toIndexedSeq

			val olds = oldCovs ++ oldCovs.collect{
				case Rdf4jStatement(_, _, coverage: IRI) =>
					getStatements(Some(coverage), None, None)
			}.flatten

			val coverageUpdates = MetadataUpdater.diff(olds, news, factory)
			val intervalUpdates = tabularExtractUpdates(hash, ingestionExtract)

			coverageUpdates ++ intervalUpdates


	private def tabularExtractUpdates(hash: Sha256Sum, extract: TabularIngestionExtract): TSC2[Seq[RdfUpdate]] =
		val objUri = vocab.getStaticObject(hash)

		val olds = getStatements(Some(objUri), Some(metaVocab.hasActualColumnNames), None).toIndexedSeq

		val news = extract.actualColumns.toIndexedSeq.map { cols =>
			val colsJson = printAsJsonArray(cols)
			factory.createStatement(objUri, metaVocab.hasActualColumnNames, vocab.lit(colsJson))
		}
		MetadataUpdater.diff(olds, news, factory) ++ acqusitionIntervalUpdates(hash, extract.interval)


	private def acqusitionIntervalUpdates(hash: Sha256Sum, interval: TimeInterval): TSC2[Seq[RdfUpdate]] =
		val acqUri = vocab.getAcquisition(hash)

		val news = IndexedSeq(
			factory.createStatement(acqUri, metaVocab.prov.startedAtTime, vocab.lit(interval.start)),
			factory.createStatement(acqUri, metaVocab.prov.endedAtTime, vocab.lit(interval.stop))
		)

		val olds = getStatements(Some(acqUri), Some(metaVocab.prov.startedAtTime), None).toIndexedSeq ++
			getStatements(Some(acqUri), Some(metaVocab.prov.endedAtTime), None)

		MetadataUpdater.diff(olds, news, factory)


	private def nRowsUpdates(hash: Sha256Sum, rowsInfo: Option[Int]): TSC2[Seq[RdfUpdate]] = rowsInfo match
		case None => Nil
		case Some(nRows) =>
			val objUri = vocab.getStaticObject(hash)
			val news = Seq(factory.createStatement(objUri, metaVocab.hasNumberOfRows, vocab.lit(nRows)))
			val olds = getStatements(Some(objUri), Some(metaVocab.hasNumberOfRows), None).toIndexedSeq
			MetadataUpdater.diff(olds, news, factory)

end TimeSeriesUploadCompleter
