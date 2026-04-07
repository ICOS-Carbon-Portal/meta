package se.lu.nateko.cp.meta.services.sparql

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import se.lu.nateko.cp.meta.QleverConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

class QleverClient(val config: QleverConfig)(using system: ActorSystem, mat: Materializer):
	private val http = Http()
	private given ExecutionContext = system.dispatcher
	private val endpoint = Uri(config.endpoint)

	def sparqlQuery(query: String, acceptMime: String): Future[HttpResponse] =
		val request = HttpRequest(
			method = HttpMethods.POST,
			uri = endpoint,
			headers = List(RawHeader("Accept", acceptMime)),
			entity = FormData("query" -> query).toEntity
		)
		http.singleRequest(request)

	def sparqlUpdate(update: String): Future[Done] =
		val formFields = config.accessToken match
			case Some(token) => Map("update" -> update, "access-token" -> token)
			case None => Map("update" -> update)
		val request = HttpRequest(
			method = HttpMethods.POST,
			uri = endpoint,
			entity = FormData(formFields).toEntity
		)
		http.singleRequest(request).flatMap: resp =>
			if resp.status.isSuccess then
				resp.entity.discardBytes().future()
			else
				resp.entity.toStrict(5.seconds).flatMap: strict =>
					Future.failed(Exception(s"QLever update failed with ${resp.status}: ${strict.data.utf8String}"))

end QleverClient
