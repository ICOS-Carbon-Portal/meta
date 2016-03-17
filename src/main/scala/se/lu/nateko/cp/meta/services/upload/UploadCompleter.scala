package se.lu.nateko.cp.meta.services.upload

import java.net.URLEncoder
import java.time.Instant

import scala.concurrent.Future
import scala.util.Try

import org.openrdf.model.URI
import org.openrdf.model.Value
import org.openrdf.model.vocabulary.OWL
import org.openrdf.model.vocabulary.RDF
import org.openrdf.model.vocabulary.RDFS

import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.api.EpicPidClient
import se.lu.nateko.cp.meta.api.PidUpdate
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.core.data.WdcggUploadCompletion
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UploadCompletionException
import se.lu.nateko.cp.meta.utils.sesame.EnrichedValueFactory
import spray.json.JsString

class UploadCompleter(server: InstanceServer, conf: UploadServiceConfig, vocab: CpmetaVocab)(implicit system: ActorSystem) {
	import system.dispatcher

	private val epic = EpicPidClient(conf.epicPid)

	def getPid(hash: Sha256Sum): String = epic.getPid(getPidSuffix(hash))

	def completeUpload(hash: Sha256Sum, info: UploadCompletionInfo): Future[String] = {
		for(
			_ <- uploadIsNotCompleteYet(hash);
			format <- getObjectSpecificationFormat(hash);
			result <- completeUpload(hash, format, info)
		) yield result
	}

	private def getPidSuffix(hash: Sha256Sum): String = hash.id

	private def uploadIsNotCompleteYet(hash: Sha256Sum): Future[Unit] = {
		val submissionUri = vocab.getSubmission(hash)
		if(server.getValues(submissionUri, vocab.prov.endedAtTime).isEmpty)
			Future.successful(())
		else
			Future.failed(new UploadCompletionException(s"Upload of $hash is already complete"))
	}

	private def getObjectSpecificationFormat(hash: Sha256Sum): Future[URI] = {
		import InstanceServer.ExactlyOne
		val dataObjUri = vocab.getDataObject(hash)

		Future.fromTry(Try{
			val objSpec = server.getUriValues(dataObjUri, vocab.hasObjectSpec, ExactlyOne).head
			server.getUriValues(objSpec, vocab.hasFormat, ExactlyOne).head
		})
	}

	private def completeUpload(hash: Sha256Sum, format: URI, info: UploadCompletionInfo): Future[String] = {
		if(format == vocab.wdcggFormat){
			for(
				_ <- writeWdcggMetadata(hash, info);
				_ <- writeUploadStopTime(hash)
			) yield vocab.getDataObject(hash).stringValue
		}else for(
			pid <- mintEpicPid(hash);
			_ <- writeUploadStopTime(hash)
		) yield pid
	}

	private def mintEpicPid(hash: Sha256Sum): Future[String] = {
		val targetUri = vocab.getDataObject(hash)
		val pidEntry = PidUpdate("URL", JsString(targetUri.toString))
		epic.create(getPidSuffix(hash), Seq(pidEntry))
			.map(_ => getPid(hash))
	}

	private def writeUploadStopTime(hash: Sha256Sum): Future[Unit] = {
		val submissionUri = vocab.getSubmission(hash)
		val stopInfo = vocab.factory.createStatement(submissionUri, vocab.prov.endedAtTime, vocab.lit(Instant.now))
		Future.fromTry(server.add(stopInfo))
	}

	private def writeWdcggMetadata(hash: Sha256Sum, info: UploadCompletionInfo): Future[Unit] = info match {
		case WdcggUploadCompletion(nRows, interVal, keyValues) => Future{
			val facts = scala.collection.mutable.Queue.empty[(URI, URI, Value)]

			val objUri = vocab.getDataObject(hash)
			facts += ((objUri, vocab.hasNumberOfRows, vocab.lit(nRows.toLong)))

			for((key, value) <- keyValues){
				val keyProp = vocab.getRelative("wdcgg/" + URLEncoder.encode(key, "UTF-8"))

				if(!server.hasStatement(Some(keyProp), None, None)){
					facts += ((keyProp, RDF.TYPE, OWL.DATATYPEPROPERTY))
					facts += ((keyProp, RDFS.SUBPROPERTYOF, vocab.hasFormatSpecificMeta))
					facts += ((keyProp, RDFS.LABEL, vocab.lit(key)))
				}
				facts += ((objUri, keyProp, vocab.lit(value)))
			}
			server.addAll(facts.map(vocab.factory.tripleToStatement))
		}

		case _ => Future.failed(new UploadCompletionException(
			"Encountered wrong type of upload completion info, must be WdcggUploadCompletion"
		))
	}

}
