package se.lu.nateko.cp.meta.services.upload.completion

import java.time.Instant

import scala.concurrent.Future
import scala.util.Try

import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.IngestionMetadataExtract
import se.lu.nateko.cp.meta.core.data.NetCdfExtract
import se.lu.nateko.cp.meta.core.data.SpatialTimeSeriesExtract
import se.lu.nateko.cp.meta.core.data.TimeSeriesExtract
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.upload.DataObjectInstanceServers


class UploadCompleter(servers: DataObjectInstanceServers, handles: HandleNetClient)(implicit system: ActorSystem) {
	import servers.{ metaVocab, vocab }
	import system.dispatcher

	def completeUpload(hash: Sha256Sum, info: UploadCompletionInfo)(implicit envri: Envri): Future[Report] = {
		for(
			(specific, server) <- Future.fromTry(getSpecificCompleter(hash, info.ingestionResult));
			specificUpdates <- specific.getUpdates(hash);
			stopTimeUpdates = getUploadStopTimeUpdates(server, hash);
			byteSizeUpdates = getBytesSizeUpdates(server, hash, info.bytes);
			report <- specific.finalize(hash);
			_ <- Future.fromTry(server.applyAll(specificUpdates ++ stopTimeUpdates ++ byteSizeUpdates))
		) yield report
	}

	private def getSpecificCompleter(
		hash: Sha256Sum,
		ingestionResult: Option[IngestionMetadataExtract]
	)(implicit envri: Envri): Try[(FormatSpecificCompleter, InstanceServer)] =
		servers.getInstServerForStaticObj(hash).map{server =>
			val completer = ingestionResult match{
				case None =>
					new PidMinter(handles, vocab)

				case Some(extract) => extract match {
					case _: TimeSeriesExtract | _: SpatialTimeSeriesExtract =>
						new TimeSeriesUploadCompleter(server, extract, handles, vocab, metaVocab)

					case netcdf: NetCdfExtract =>
						new NetCdfUploadCompleter(server, netcdf, handles, vocab, metaVocab)
				}
			}
			(completer, server)
		}

	private def getUploadStopTimeUpdates(server: InstanceServer, hash: Sha256Sum)(implicit envri: Envri): Seq[RdfUpdate] = {
		val submissionUri = vocab.getSubmission(hash)
		if(server.hasStatement(Some(submissionUri), Some(metaVocab.prov.endedAtTime), None)) Nil
		else {
			val stopInfo = vocab.factory.createStatement(submissionUri, metaVocab.prov.endedAtTime, vocab.lit(Instant.now))
			Seq(RdfUpdate(stopInfo, true))
		}
	}

	private def getBytesSizeUpdates(server: InstanceServer, hash: Sha256Sum, size: Long)(implicit envri: Envri): Seq[RdfUpdate] = {
		val dobj = vocab.getStaticObject(hash)
		if(server.hasStatement(Some(dobj), Some(metaVocab.hasSizeInBytes), None)) Nil //byte size cannot change for same hash
		else{
			val sizeInfo = vocab.factory.createStatement(dobj, metaVocab.hasSizeInBytes, vocab.lit(size))
			Seq(RdfUpdate(sizeInfo, true))
		}
	}

}
