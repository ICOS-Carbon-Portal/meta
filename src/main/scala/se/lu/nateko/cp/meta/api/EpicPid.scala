package se.lu.nateko.cp.meta.api

import akka.actor.ActorSystem
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import se.lu.nateko.cp.meta.{ConfigLoader, EpicPidConfig}
import scala.concurrent.{Await, Future}
import HttpMethods._

class EpicPid(config: EpicPidConfig)(implicit system: ActorSystem) {

	implicit val materializer = ActorMaterializer()

	def listPids(): HttpResponse = {

		val authorization = headers.Authorization(BasicHttpCredentials(config.userName, config.password))
		val accept = headers.Accept(MediaTypes.`application/json`)

		val request = HttpRequest(
			uri = config.url + config.userName,
			headers = List(authorization, accept)
		)
println(request)
		val responseFuture: Future[HttpResponse] =
			Http().singleRequest(request)

		Await.result(responseFuture, 5 second)

//		responseFuture.onComplete{
//			case Success => println("Success")
//			case Failure => println("Failure")
//		}


	}

}

object EpicPid{

	implicit val system = ActorSystem("epic")

	def default: EpicPid = new EpicPid(ConfigLoader.default.dataUploadService.epicPid)
	def apply(config: EpicPidConfig) = new EpicPid(config)

}