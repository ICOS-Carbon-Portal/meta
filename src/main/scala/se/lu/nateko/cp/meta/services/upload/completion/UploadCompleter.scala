package se.lu.nateko.cp.meta.services.upload.completion

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.IngestionMetadataExtract
import se.lu.nateko.cp.meta.core.data.NetCdfExtract
import se.lu.nateko.cp.meta.core.data.SpatialTimeSeriesExtract
import se.lu.nateko.cp.meta.core.data.TimeSeriesExtract
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.services.upload.DataObjectInstanceServers

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import se.lu.nateko.cp.meta.services.MetadataException


class UploadCompleter(servers: DataObjectInstanceServers, handles: HandleNetClient)(using ExecutionContext):
	import servers.{ metaVocab, vocab }
	import TriplestoreConnection.hasStatement

	def completeUpload(hash: Sha256Sum, info: UploadCompletionInfo)(using Envri): Future[Report] =
		for
			server <- Future.fromTry(servers.getInstServerForStaticObj(hash).toTry(new MetadataException(_)))

			completer = info.ingestionResult.fold(new PidMinter(handles, vocab)):
				case tsOrSpat: (TimeSeriesExtract | SpatialTimeSeriesExtract) =>
					new TimeSeriesUploadCompleter(tsOrSpat, handles, vocab, metaVocab)

				case netcdf: NetCdfExtract =>
					new NetCdfUploadCompleter(netcdf, handles, vocab, metaVocab)

			updates = server.access:
				completer.getUpdates(hash) ++ getUploadStopTimeUpdates(hash) ++ getBytesSizeUpdates(hash, info.bytes)

			report <- completer.finalize(hash)

			_ <- Future.fromTry(server.applyAll(updates)())

		yield report


	private def getUploadStopTimeUpdates(hash: Sha256Sum)(using Envri, TriplestoreConnection): Seq[RdfUpdate] =
		val submissionUri = vocab.getSubmission(hash)
		if(hasStatement(Some(submissionUri), Some(metaVocab.prov.endedAtTime), None)) Nil
		else {
			val stopInfo = vocab.factory.createStatement(submissionUri, metaVocab.prov.endedAtTime, vocab.lit(Instant.now))
			Seq(RdfUpdate(stopInfo, true))
		}


	private def getBytesSizeUpdates(hash: Sha256Sum, size: Long)(using Envri, TriplestoreConnection): Seq[RdfUpdate] =
		val dobj = vocab.getStaticObject(hash)
		if(hasStatement(Some(dobj), Some(metaVocab.hasSizeInBytes), None)) Nil //byte size cannot change for same hash
		else{
			val sizeInfo = vocab.factory.createStatement(dobj, metaVocab.hasSizeInBytes, vocab.lit(size))
			Seq(RdfUpdate(sizeInfo, true))
		}

end UploadCompleter
