package se.lu.nateko.cp.meta.services.upload.completion

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.NetCdfExtract
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.upload.MetadataUpdater

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import se.lu.nateko.cp.meta.api.RdfLens.DobjLens


private class NetCdfUploadCompleter(
	result: NetCdfExtract,
	handles: HandleNetClient,
	lens: DobjLens,
	vocab: CpVocab,
	metaVocab: CpmetaVocab
)(using Envri) extends PidMinter(handles, vocab):

	import TriplestoreConnection.getStatements
	private val factory = vocab.factory

	override def getUpdates(hash: Sha256Sum)(using TriplestoreConnection): Seq[RdfUpdate] =

		val olds = result.varInfo.flatMap{vinfo =>
			val vUri = vocab.getVarInfo(hash, vinfo.name)

			IndexedSeq(metaVocab.hasMinValue, metaVocab.hasMaxValue).flatMap{prop =>
				getStatements(vUri, prop, null)(using lens)
			}
		}

		val news = result.varInfo.flatMap{vinfo =>
			val vUri = vocab.getVarInfo(hash, vinfo.name)

			Seq(metaVocab.hasMinValue -> vinfo.min, metaVocab.hasMaxValue -> vinfo.max).map{
				case (prop, m) => factory.createStatement(vUri, prop, vocab.lit(m))
			}
		}
		MetadataUpdater.diff(olds, news, factory)
