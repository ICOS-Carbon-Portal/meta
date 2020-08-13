package se.lu.nateko.cp.meta.upload

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import java.net.URI
import scala.concurrent.Future
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

object L3UpdateWorkbench extends CpmetaJsonProtocol{
	implicit val system = ActorSystem("l3update_workbench")
	import system.dispatcher

	val uploadConfBase = new CpUploadClient.Config(
		"???",
		"meta.icos-cp.eu",
		"data.icos-cp.eu"
		//Some(Uri("http://127.0.0.1:9094")),
		//Some(Uri("http://127.0.0.1:9010"))
	)

	def uploadClient(cpAuthToken: String) = new CpUploadClient(uploadConfBase.copy(cpauthToken = cpAuthToken))

	val sparql = new SparqlHelper(new java.net.URI(s"${uploadConfBase.metaBase}/sparql"))
	private val http = Http()

	def getUploadDto(uri: URI): Future[DataObjectDto] = http.singleRequest(
			HttpRequest(
				uri = uploadConfBase.metaBase.withPath(Uri.Path./("dtodownload")).withQuery(Uri.Query("uri" -> uri.toString))
			)
		).flatMap{
			resp => Unmarshal(resp).to[DataObjectDto]
		}
}
