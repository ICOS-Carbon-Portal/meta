package se.lu.nateko.cp.meta.test

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object AkkaBugReport {

	def run(): Unit = {
		val uriStr = "http://akka.io:2345/"

		val system = ActorSystem("bug_report", ConfigFactory.empty())

		val startTime = System.currentTimeMillis()

		println(s"Sending single HTTP GET request to $uriStr ...")
		val fut = Http(system).singleRequest(HttpRequest(uri = uriStr))

		val resp = Await.ready(fut, Duration.Inf).value.get

		val elapsed = System.currentTimeMillis() - startTime

		println(s"Elapsed $elapsed ms, result was:\n$resp")
		system.terminate()
	}

}
