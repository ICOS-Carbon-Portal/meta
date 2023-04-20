package se.lu.nateko.cp.meta.test

import java.net.URI

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.sparql.BoundUri
import se.lu.nateko.cp.meta.test.utils.SparqlClient
import se.lu.nateko.cp.meta.services.citation.CitationClientImpl
import se.lu.nateko.cp.meta.ingestion.badm.BadmEntry
import se.lu.nateko.cp.meta.icos.EtcMetaSource
import eu.icoscp.envri.Envri
import scala.collection.concurrent.TrieMap
import se.lu.nateko.cp.meta.ConfigLoader
import se.lu.nateko.cp.meta.mail.SendMail

object Playground {

	given system: ActorSystem = ActorSystem("playground")
	import system.dispatcher
	given envri: Envri = Envri.ICOS

	val metaConf = se.lu.nateko.cp.meta.ConfigLoader.default

	val handles = new HandleNetClient(
		metaConf.dataUploadService.handle.copy(prefix = Map(Envri.ICOS -> "11676"), dryRun = false)
	)

	val sparql = new SparqlClient(new URI("https://meta.icos-cp.eu/sparql"))
	val citer = CitationClientImpl(Nil, metaConf.citations, TrieMap.empty, TrieMap.empty)

	def stop() = system.terminate()

	def create(postfix: String, targetUrl: String): Unit = wait{
		handles.createOrRecreate(postfix, new java.net.URL(targetUrl))
	}

//	def create(targetUrl: String): String = wait{
//		epic.createRandom(Seq(PidUpdate("URL", JsString(targetUrl))))
//	}

//	def addHash(postfix: String, hash: String): Unit = wait{
//		for(
//			entries <- handles.get(postfix);
//			oldEntries = entries.reverse.tail.map(EpicPidClient.toUpdate);
//			res <- epic.update(postfix, PidUpdate("SHA256", JsString(hash)) +: oldEntries)
//		) yield res
//	}

	def delete(postfix: String): Unit = wait{
		handles.delete(postfix)
	}

	def list(): Unit = wait(handles.list) foreach println
	def listOrphans(): Unit = wait(getOrphans) foreach println

	def print(suffix: String): Unit = handles.get(suffix) foreach println

	def getOrphans: Future[Seq[String]] = handles.list.flatMap{allPids =>
		val query = orphanPidFilteringQuery(allPids)

		sparql.select(query).map{res =>
			res.results.bindings.map(binding => binding.get("dobj").collect{
				case BoundUri(uri) => uri.getPath.split('/').last
			}).flatten
		}
	}

	def deleteAllOrphans(): Done = wait{
		getOrphans.flatMap{orphanPids =>
			val seed = Future.successful(Done)
			orphanPids.foldLeft[Future[Done]](seed)((fut, suff) => fut.flatMap(_ => handles.delete(suff)))
		}
	}

	private def wait[T](fut: Future[T]): T = Await.result(fut, Duration.Inf)

	private def orphanPidFilteringQuery(allPids: Seq[String]): String = {
		val valsClause = "VALUES ?dobj {<" + allPids.map("https://meta.icos-cp.eu/objects/" + _).mkString("> <") + ">}"
		s"""select ?dobj where{
			|$valsClause
			|FILTER NOT EXISTS{
				|?dobjReal a <http://meta.icos-cp.eu/ontologies/cpmeta/DataObject> .
				|FILTER(LCASE(STR(?dobj)) = LCASE(STR(?dobjReal)))
			|}
		}""".stripMargin
	}

	def etcStationTable(badms: Seq[BadmEntry]): Seq[Seq[String]] = {
		def toByVarLookup(bs: Seq[BadmEntry]): Map[String, String] =
			bs.flatMap(b => b.values.map(v => (b.variable + "/" + v.variable) -> v.valueStr)).toMap

		val tableVars = List("GRP_HEADER/SITE_ID", "GRP_HEADER/SITE_NAME")

		badms.groupBy(_.stationId).toSeq.map{case (stIdOpt, badms) =>
			val lookup = toByVarLookup(badms)
			stIdOpt.map(_.id).getOrElse("") :: tableVars.map(v => lookup.get(v).getOrElse(""))
		}
	}

	def mailSender = {
		val conf = ConfigLoader.default.stationLabelingService.mailing
		SendMail(conf.copy(mailSendingActive = true), system.log)
	}

//	def printEtcStationsTable(): Unit = etcMetaSrc.fetchFromEtc().map(etcStationTable).foreach{rows =>
//		rows.sortBy(_.head).map(_.mkString("\t")).foreach(println)
//	}
}
