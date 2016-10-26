package se.lu.nateko.cp.meta.test

import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.api.EpicPidClient
import se.lu.nateko.cp.meta.api.PidUpdate
import spray.json.JsString
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.Future
import java.net.URL
import se.lu.nateko.cp.meta.test.utils.SparqlClient
import se.lu.nateko.cp.meta.core.sparql.BoundUri

object Playground {

	implicit val system = ActorSystem("playground")
	import system.dispatcher

	val epic = EpicPidClient.default
	val sparql = new SparqlClient(new URL("https://meta.icos-cp.eu/sparql"))

	def stop() = system.terminate()

	def create(postfix: String, targetUrl: String): Unit = wait{
		epic.create(postfix, Seq(PidUpdate("URL", JsString(targetUrl))))
	}

	def create(targetUrl: String): String = wait{
		epic.create(Seq(PidUpdate("URL", JsString(targetUrl))))
	}

	def addHash(postfix: String, hash: String): Unit = wait{
		for(
			entries <- epic.get(postfix);
			oldEntries = entries.reverse.tail.map(EpicPidClient.toUpdate);
			res <- epic.update(postfix, PidUpdate("SHA256", JsString(hash)) +: oldEntries)
		) yield res
	}

	def delete(postfix: String): Unit = wait{
		epic.delete(postfix)
	}

	def list(): Unit = wait(epic.list) foreach println
	def listOrphans(): Unit = wait(getOrphans) foreach println

	def print(suffix: String): Unit = wait(epic.get(suffix)) foreach println

	def getOrphans: Future[Seq[String]] = epic.list.flatMap{allPids =>
		val query = orphanPidFilteringQuery(allPids)

		sparql.select(query).map{res =>
			res.results.bindings.map(binding => binding.get("dobj").collect{
				case BoundUri(uri) => uri.getPath.split('/').last
			}).flatten
		}
	}

	def deleteAllOrphans(): Unit = wait{
		getOrphans.flatMap{orphanPids =>
			val seed = Future.successful(())
			orphanPids.foldLeft(seed)((fut, suff) => fut.flatMap(_ => epic.delete(suff)))
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
