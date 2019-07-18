package se.lu.nateko.cp.meta.upload

import java.net.URI

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom.File
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.ext.AjaxException
import org.scalajs.dom.raw.XMLHttpRequest
import JsonSupport._
import play.api.libs.json._
import se.lu.nateko.cp.meta.{SubmitterProfile, ObjectUploadDto, DataObjectDto, DocObjectDto}
import se.lu.nateko.cp.meta.core.data.{Envri, EnvriConfig}
import se.lu.nateko.cp.meta.core.data.Envri.Envri

object Backend {

	import SparqlQueries._

	private def whoAmI: Future[Option[String]] =
		Ajax.get("/whoami", withCredentials = true)
		.recoverWith(recovery("fetch user information"))
		.map(xhr =>
			parseTo[JsObject](xhr).value("email") match {
				case JsString(email) => Some(email)
				case _ => None
			})

	private def envri: Future[Envri] = Ajax.get("/upload/envri")
		.recoverWith(recovery("fetch envri"))
		.map(parseTo[Envri])

	private def authHost: Future[EnvriConfig] = Ajax.get("/upload/envriconfig")
		.recoverWith(recovery("fetch envri config"))
		.map(parseTo[EnvriConfig])

	def fetchConfig: Future[InitAppInfo] = whoAmI.zip(envri).zip(authHost).map {
		case ((whoAmI, envri), authHost) => InitAppInfo(whoAmI, envri, authHost)
	}

	def submitterIds: Future[IndexedSeq[SubmitterProfile]] =
		Ajax.get("/upload/submitterids", withCredentials = true)
			.recoverWith(recovery("fetch the list of available submitter ids"))
			.map(parseTo[IndexedSeq[SubmitterProfile]])

	def stationInfo(orgClass: Option[URI])(implicit envri: Envri.Envri): Future[IndexedSeq[Station]] =
		sparqlSelect(stations(orgClass)).map(_.map(toStation))

	def getObjSpecs(implicit envri: Envri.Envri): Future[IndexedSeq[ObjSpec]] =
		sparqlSelect(objSpecs).map(_.map(toObjSpec))

	def tryIngestion(
		file: File, spec: ObjSpec, nRows: Option[Int]
	)(implicit envriConfig: EnvriConfig): Future[Unit] = if(spec.hasDataset){

		val nRowsQuery = nRows.map(nRows => s"&nRows=$nRows").getOrElse("")
		val url = s"${envriConfig.dataPrefix}tryingest?specUri=${spec.uri}$nRowsQuery"
		Ajax
			.put(url, file)
			.recoverWith(recovery("upload data object contents"))
			.flatMap(xhr => xhr.status match {
				case 200 => Future.successful(())
				case _ => Future.failed(new Exception(xhr.responseText))
			})
	} else Future.successful(())

	def sparqlSelect(query: String): Future[IndexedSeq[Binding]] = Ajax
		.post("/sparql", query, responseType = "application/json")
		.recoverWith(recovery("execute a SPARQL query"))
		.map(xhr =>
			(parseTo[JsObject](xhr) \ "results" \ "bindings")
				.validate[JsArray]
				.map(_.value.collect(parseBinding))
				.get
		)

	def submitMetadata(dto: ObjectUploadDto): Future[URI] = {
		val json = dto match {
			case dataObjectDto: DataObjectDto => Json.toJson(dataObjectDto)
			case documentObjectDto: DocObjectDto => Json.toJson(documentObjectDto)
		}
		Ajax.post("/upload", Json.prettyPrint(json), headers = Map("Content-Type" -> "application/json"), withCredentials = true)
			.recoverWith(recovery("upload data object metadata"))
			.map(xhr => new URI(xhr.responseText))
	}

	def uploadFile(file: File, path: URI): Future[String] = Ajax
		.put(path.toString, file, headers = Map("Content-Type" -> "application/octet-stream"), withCredentials = true)
		.recoverWith(recovery("upload data object contents"))
		.map(_.responseText)

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
