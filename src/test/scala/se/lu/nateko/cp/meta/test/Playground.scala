package se.lu.nateko.cp.meta.test

import java.net.URL

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.sparql.BoundUri
import se.lu.nateko.cp.meta.test.utils.SparqlClient

object Playground {

	implicit val system = ActorSystem("playground")
	import system.dispatcher
	implicit val mat = ActorMaterializer()

	val handles = {
		val conf = se.lu.nateko.cp.meta.ConfigLoader.default.dataUploadService.handle
		new HandleNetClient(conf.copy(prefix = "11676", dryRun = false))
	}

	val sparql = new SparqlClient(new URL("https://meta.icos-cp.eu/sparql"))

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
}
