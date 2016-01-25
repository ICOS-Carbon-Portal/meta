package se.lu.nateko.cp.meta

import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.api.EpicPidClient
import se.lu.nateko.cp.meta.api.PidUpdate
import spray.json.JsString
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.Future

object Playground {

	implicit val system = ActorSystem("playground")
	import system.dispatcher

	val client = EpicPidClient.default

	def stop(): Unit = system.shutdown()

	def create(postfix: String, targetUrl: String): Unit = wait{
		client.create(postfix, Seq(PidUpdate("URL", JsString(targetUrl))))
	}

	def create(targetUrl: String): String = wait{
		client.create(Seq(PidUpdate("URL", JsString(targetUrl))))
	}

	def addHash(postfix: String, hash: String): Unit = wait{
		for(
			entries <- client.get(postfix);
			oldEntries = entries.reverse.tail.map(EpicPidClient.toUpdate);
			res <- client.update(postfix, PidUpdate("SHA256", JsString(hash)) +: oldEntries)
		) yield res
	}

	def delete(postfix: String): Unit = wait{
		client.delete(postfix)
	}

	def list(): Unit = wait(client.list) foreach println

	def print(suffix: String): Unit = wait(client.get(suffix)) foreach println

	private def wait[T](fut: Future[T]): T = Await.result(fut, Duration.Inf)

}