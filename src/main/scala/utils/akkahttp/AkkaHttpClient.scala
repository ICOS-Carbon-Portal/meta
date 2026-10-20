package se.lu.nateko.cp.meta.utils.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings

import scala.concurrent.Future

trait HttpClient:
	def singleRequest(request: HttpRequest, settings: ConnectionPoolSettings): Future[HttpResponse]

class AkkaHttpClient(using system: ActorSystem) extends HttpClient:
	def singleRequest(request: HttpRequest, settings: ConnectionPoolSettings): Future[HttpResponse] =
		Http().singleRequest(request, settings = settings)
