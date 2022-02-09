package se.lu.nateko.cp.meta.upload

import java.net.URI

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils.encodeURIComponent
import org.scalajs.dom.File
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.ext.AjaxException
import org.scalajs.dom.raw.XMLHttpRequest
import JsonSupport._
import play.api.libs.json._
import se.lu.nateko.cp.meta.{SubmitterProfile, UploadDto}
import se.lu.nateko.cp.meta.core.data.{Envri, EnvriConfig}
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.doi.Doi

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
			.flatMap{ s =>
				if(s.isEmpty)
					Future.failed(new Exception("""You are not authorized to upload data.
					Please contact us to if you would like to get the permission."""))
				else Future.successful(s)
			}

	def stationInfo(orgClass: Option[URI], producingOrg: Option[URI])(implicit envri: Envri.Envri): Future[IndexedSeq[Station]] =
		sparqlSelect(stations(orgClass, producingOrg)).map(_.map(toStation))

	def getObjSpecs(implicit envri: Envri.Envri): Future[IndexedSeq[ObjSpec]] =
		sparqlSelect(objSpecs).map(_.map(toObjSpec))

	def getSites(station: URI): Future[IndexedSeq[NamedUri]] =
		sparqlSelect(sites(station)).map(_.map(toSite)).map(disambiguateNames)

	def getSamplingPoints(site: URI): Future[IndexedSeq[SamplingPoint]] =
		sparqlSelect(samplingpoints(site)).map((_.map(toSamplingPoint)))

	def getL3SpatialCoverages(implicit envri: Envri.Envri): Future[IndexedSeq[SpatialCoverage]] =
		if(envri == Envri.SITES) Future.successful(IndexedSeq.empty)
		else sparqlSelect(l3spatialCoverages).map(_.map(toSpatialCoverage))

	private def disambiguateNames(list: IndexedSeq[NamedUri]): IndexedSeq[NamedUri] =
		list.groupBy(_.name).valuesIterator.flatMap( g =>
			if (g.length <= 1) g
			else g.map{nUri =>
				val uriSegm = nUri.uri.getPath().split('/').last
				nUri.copy(name = s"${nUri.name} ($uriSegm)")
			}
		).toIndexedSeq.sortBy(_.name)

	def getPeople(implicit envri: Envri.Envri): Future[IndexedSeq[NamedUri]] =
		sparqlSelect(people).map(_.map(toPerson)).map(disambiguateNames)

	def getOrganizations(implicit envri: Envri.Envri): Future[IndexedSeq[NamedUri]] =
		sparqlSelect(organizations).map(_.map(toOrganization)).map(disambiguateNames)

	def getDatasetColumns(dataset: URI): Future[IndexedSeq[DatasetVar]] =
		sparqlSelect(datasetColumnQuery(dataset)).map(_.map(toDatasetVar))
	def getDatasetVariables(dataset: URI): Future[IndexedSeq[DatasetVar]] =
		sparqlSelect(datasetVariableQuery(dataset)).map(_.map(toDatasetVar))

	def tryIngestion(
		file: File, spec: ObjSpec, nRows: Option[Int], varnames: Option[Seq[String]]
	)(implicit envriConfig: EnvriConfig): Future[Unit] = {

		val firstVarName: Option[String] = varnames.flatMap(_.headOption).filter(_ => spec.dataLevel == 3)

		if(spec.dataset.nonEmpty && (spec.dataLevel <= 2 || firstVarName.isDefined)){

			val nRowsQ = nRows.fold("")(nr => s"&nRows=$nr")
			val varsQ = varnames.fold(""){vns =>
				val varsJson = encodeURIComponent(Json.toJson(vns).toString)
				s"&varnames=$varsJson"
			}

			val url = s"https://${envriConfig.dataHost}/tryingest?specUri=${spec.uri}$nRowsQ$varsQ"
			Ajax
			.put(url, file)
			.recoverWith {
				case AjaxException(xhr) =>
					val msg = if(xhr.responseText.isEmpty)
						"File could not be found"
					else xhr.responseText

					Future.failed(new Exception(msg))
			}
			.flatMap(xhr => xhr.status match {
				case 200 => Future.successful(())
				case _ => Future.failed(new Exception(xhr.responseText))
			})
		} else Future.successful(())
	}

	def sparqlSelect(query: String): Future[IndexedSeq[Binding]] = Ajax
		.post("/sparql", query)
		.recoverWith(recovery("execute a SPARQL query"))
		.map(xhr =>
			(parseTo[JsObject](xhr) \ "results" \ "bindings")
				.validate[JsArray]
				.map(_.value.collect(parseBinding))
				.get.toVector
		)

	def submitMetadata[T : Writes](dto: T): Future[URI] = {
		val json = Json.toJson(dto)
		Ajax.post("/upload", Json.prettyPrint(json), headers = Map("Content-Type" -> "application/json"), withCredentials = true)
			.recoverWith(recovery("upload metadata"))
			.map(xhr => new URI(xhr.responseText))
	}

	def uploadFile(file: File, dataURL: URI): Future[String] = Ajax
		.put(dataURL.toString, file, headers = Map("Content-Type" -> "application/octet-stream"), withCredentials = true)
		.recoverWith(recovery("upload file"))
		.map(_.responseText)

	def getMetadata(uri: URI): Future[UploadDto] = Ajax.get(s"/dtodownload?uri=$uri")
		.recoverWith(recovery("fetch existing object"))
		.map(parseTo[UploadDto])

	def createDraftDoi(uri: URI): Future[Doi] = Ajax
		.post(s"/dois/createDraft", Json.prettyPrint(Json.toJson(uri)), headers = Map("Content-Type" -> "application/json"), withCredentials = true)
		.recoverWith(recovery("create draft DOI"))
		.map(parseTo[Doi])

	def getKeywordList(implicit envri: Envri.Envri): Future[IndexedSeq[String]] =
		if (envri == Envri.SITES) Future.successful(IndexedSeq.empty)
		else Ajax
			.get("/uploadgui/gcmdkeywords.json")
			.recoverWith(recovery("fetch keyword list"))
			.map(parseTo[IndexedSeq[String]])

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
