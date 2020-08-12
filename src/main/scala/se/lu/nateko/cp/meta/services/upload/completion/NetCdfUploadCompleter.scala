package se.lu.nateko.cp.meta.services.upload.completion

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.NetCdfExtract
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.upload.MetadataUpdater


private class NetCdfUploadCompleter(
	server: InstanceServer,
	result: NetCdfExtract,
	handles: HandleNetClient,
	vocab: CpVocab,
	metaVocab: CpmetaVocab
)(implicit ctxt: ExecutionContext, envri: Envri) extends PidMinter(handles, vocab) {

	private val factory = vocab.factory

	override def getUpdates(hash: Sha256Sum): Future[Seq[RdfUpdate]] = Future{

		val olds = result.varInfo.flatMap{vinfo =>
			val vUri = vocab.getVarInfo(hash, vinfo.name)

			Seq(metaVocab.hasMinValue, metaVocab.hasMaxValue).flatMap{prop =>
				server.getStatements(Some(vUri), Some(prop), None)
			}
		}

		val news = result.varInfo.flatMap{vinfo =>
			val vUri = vocab.getVarInfo(hash, vinfo.name)

			Seq(metaVocab.hasMinValue -> vinfo.min, metaVocab.hasMaxValue -> vinfo.max).map{
				case (prop, m) => factory.createStatement(vUri, prop, vocab.lit(m))
			}
		}
		MetadataUpdater.diff(olds, news, factory)
	}

}
