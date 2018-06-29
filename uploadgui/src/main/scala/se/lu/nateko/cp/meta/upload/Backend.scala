package se.lu.nateko.cp.meta.upload

import java.net.URI

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.ext.AjaxException
import org.scalajs.dom.raw.XMLHttpRequest

import JsonSupport.uploadMetadataDtoWrites
import play.api.libs.json._
import se.lu.nateko.cp.meta.UploadMetadataDto

object Backend {

	import SparqlQueries._

	def submitterIds: Future[IndexedSeq[String]] =
		Ajax.get("/upload/submitterids", withCredentials = true)
			.recoverWith(recovery("fetch the list of available submitter ids"))
			.map(parseTo[IndexedSeq[String]])

	def sitesStationInfo = stationInfo(sitesStations)
	def stationInfo(query: String): Future[IndexedSeq[Station]] = sparqlSelect(query).map(_.map(toStation))

	def getSitesObjSpecs = getObjSpecs(sitesObjSpecs)
	def getObjSpecs(query: String): Future[IndexedSeq[ObjSpec]] = sparqlSelect(query).map(_.map(toObjSpec))

	def sparqlSelect(query: String): Future[IndexedSeq[Binding]] = Ajax
		.post("https://meta.icos-cp.eu/sparql", query, responseType = "application/json")
		.recoverWith(recovery("execute a SPARQL query"))
		.map(xhr =>
			(parseTo[JsObject](xhr) \ "results" \ "bindings")
				.validate[JsArray]
				.map(_.value.collect(parseBinding))
				.get
		)

	def submitMetadata(dto: UploadMetadataDto): Future[URI] = {
		val json = Json.toJson(dto)
		Ajax.post("/upload", Json.prettyPrint(json), headers = Map("Content-Type" -> "application/json"), withCredentials = true)
			.recoverWith(recovery("upload data object metadata"))
			.map(xhr => new URI(xhr.responseText))
	}

	private val parseBinding: PartialFunction[JsValue, Binding] = {
		case b: JsObject => b.fields.map{
			case (key, v) => key -> (v \ "value").validate[String].get
		}.toMap
	}

	private def parseTo[T : Reads](xhr: XMLHttpRequest): T = {
		Json.parse(xhr.responseText).as[T]
	}

	private def recovery(hint: String): PartialFunction[Throwable, Future[XMLHttpRequest]] = {
		case AjaxException(xhr) =>
			val msg = if(xhr.responseText.isEmpty)
				s"Got HTTP status ${xhr.status} when trying to $hint"
			else s"Error when trying to $hint: " + xhr.responseText

			Future.failed(new Exception(msg))
	}
}
