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
import scala.util.Success
import scala.util.Failure

class UploadCompleter(servers: DataObjectInstanceServers, conf: UploadServiceConfig, vocab: CpmetaVocab)(implicit system: ActorSystem) {
	import system.dispatcher

	private val epic = EpicPidClient(conf.epicPid)

	def getPid(hash: Sha256Sum): String = epic.getPid(getPidSuffix(hash))

	def completeUpload(hash: Sha256Sum, info: UploadCompletionInfo): Future[String] = {
		for(
			(format, server) <- Future.fromTry(getFormatAndServer(hash));
			result <- completeUpload(server, hash, format, info)
		) yield result
	}

	private def getPidSuffix(hash: Sha256Sum): String = hash.id

	private def getFormatAndServer(hash: Sha256Sum): Try[(URI, InstanceServer)] =
		for(
			objSpec <- servers.getDataObjSpecification(hash);
			format <- servers.getObjSpecificationFormat(objSpec);
			server <- servers.getInstServerForFormat(format);
			_ <- uploadIsNotCompleteYet(hash, server)
		) yield (format, server)

	private def uploadIsNotCompleteYet(hash: Sha256Sum, server: InstanceServer): Try[Unit] = {
		val submissionUri = vocab.resources.getSubmission(hash)
		if(server.getValues(submissionUri, vocab.prov.endedAtTime).isEmpty)
			Success(())
		else
			Failure(new UploadCompletionException(s"Upload of $hash is already complete"))
	}

	private def completeUpload(server: InstanceServer, hash: Sha256Sum, format: URI, info: UploadCompletionInfo): Future[String] = {
		if(format == vocab.wdcggFormat){
			for(
				_ <- writeWdcggMetadata(server, hash, info);
				_ <- writeUploadStopTime(server, hash)
			) yield vocab.getDataObject(hash).stringValue
		}else for(
			pid <- mintEpicPid(hash);
			_ <- writeUploadStopTime(server, hash)
		) yield pid
	}

	private def mintEpicPid(hash: Sha256Sum): Future[String] = {
		val targetUri = vocab.getDataObject(hash)
		val pidEntry = PidUpdate("URL", JsString(targetUri.toString))
		epic.create(getPidSuffix(hash), Seq(pidEntry))
			.map(_ => getPid(hash))
	}

	private def writeUploadStopTime(server: InstanceServer, hash: Sha256Sum): Future[Unit] = {
		val submissionUri = vocab.resources.getSubmission(hash)
		val stopInfo = vocab.factory.createStatement(submissionUri, vocab.prov.endedAtTime, vocab.lit(Instant.now))
		Future.fromTry(server.add(stopInfo))
	}

	private def writeWdcggMetadata(server: InstanceServer, hash: Sha256Sum, info: UploadCompletionInfo): Future[Unit] = info match {
		case WdcggUploadCompletion(nRows, interVal, keyValues) => Future{
			val facts = scala.collection.mutable.Queue.empty[(URI, URI, Value)]

			val objUri = vocab.getDataObject(hash)
			facts += ((objUri, vocab.hasNumberOfRows, vocab.lit(nRows.toLong)))

			val productionUri = vocab.resources.getProduction(hash)
			facts += ((productionUri, vocab.prov.startedAtTime, vocab.lit(interVal.start)))
			facts += ((productionUri, vocab.prov.endedAtTime, vocab.lit(interVal.stop)))

			for((key, value) <- keyValues){
				val keyProp = vocab.getRelative("wdcgg/", key)

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
