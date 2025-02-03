package se.lu.nateko.cp.meta.services.upload.completion

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.api.RdfLens.DobjLens
import se.lu.nateko.cp.meta.api.{HandleNetClient, RdfLens}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{IngestionMetadataExtract, NetCdfExtract, SpatialTimeSeriesExtract, TimeSeriesExtract, UploadCompletionInfo}
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, RdfUpdate, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.services.upload.DataObjectInstanceServers
import se.lu.nateko.cp.meta.utils.rdf4j.toJava

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


class UploadCompleter(servers: DataObjectInstanceServers, handles: HandleNetClient)(using ExecutionContext):
	import servers.{ metaVocab, vocab }
	import TriplestoreConnection.{TSC, hasStatement}

	def completeUpload(hash: Sha256Sum, info: UploadCompletionInfo)(using Envri): Future[Report] =
		for
			server <- Future.fromTry(servers.getInstServerForStaticObj(hash).toTry(new MetadataException(_)))
			completer = info.ingestionResult.fold(new PidMinter(handles, vocab)):

				case tsOrSpat: (TimeSeriesExtract | SpatialTimeSeriesExtract) =>
					new TimeSeriesUploadCompleter(tsOrSpat, dobjLens(server), handles, vocab, metaVocab)

				case netcdf: NetCdfExtract =>
					new NetCdfUploadCompleter(netcdf, handles, dobjLens(server), vocab, metaVocab)

			updates = server.access:
				completer.getUpdates(hash) ++ getUploadStopTimeUpdates(hash) ++ getBytesSizeUpdates(hash, info.bytes)

			report <- completer.finalize(hash)

			_ <- Future.fromTry(server.applyAll(updates)())

		yield report

	private def dobjLens(server: InstanceServer): DobjLens =
		RdfLens.dobjLens(server.writeContext.toJava, server.readContexts.map(_.toJava))

	private def getUploadStopTimeUpdates(hash: Sha256Sum)(using Envri, TSC): Seq[RdfUpdate] =
		val submissionUri = vocab.getSubmission(hash)
		if hasStatement(submissionUri, metaVocab.prov.endedAtTime, null) then Nil
		else
			val stopInfo = vocab.factory.createStatement(submissionUri, metaVocab.prov.endedAtTime, vocab.lit(Instant.now))
			Seq(RdfUpdate(stopInfo, true))


	private def getBytesSizeUpdates(hash: Sha256Sum, size: Long)(using Envri, TSC): Seq[RdfUpdate] =
		val dobj = vocab.getStaticObject(hash)
		if hasStatement(dobj, metaVocab.hasSizeInBytes, null) then Nil //byte size cannot change for same hash
		else
			val sizeInfo = vocab.factory.createStatement(dobj, metaVocab.hasSizeInBytes, vocab.lit(size))
			Seq(RdfUpdate(sizeInfo, true))

end UploadCompleter
