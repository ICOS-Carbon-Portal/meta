package se.lu.nateko.cp.meta.upload

import java.net.URI

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils.encodeURIComponent
import scala.scalajs.js.Thenable.Implicits.thenable2future
import org.scalajs.dom.File
import org.scalajs.dom.fetch
import org.scalajs.dom.raw.XMLHttpRequest
import org.scalajs.dom.RequestInit
import org.scalajs.dom.Headers
import org.scalajs.dom.RequestCredentials
import org.scalajs.dom.Response
import org.scalajs.dom.HttpMethod
import JsonSupport.given
import play.api.libs.json.*
import se.lu.nateko.cp.meta.{SubmitterProfile, UploadDto}
import se.lu.nateko.cp.meta.core.data.{Envri, EnvriConfig}
import se.lu.nateko.cp.doi.Doi
import scala.scalajs.js.Dictionary

object Backend {

	import SparqlQueries.*

	private def whoAmI: Future[Option[String]] =
		fetchOk("fetch user information", "/whoami", new RequestInit{credentials = RequestCredentials.include})
		.flatMap(parseTo[JsObject](_))
		.map(_.value("email") match {
			case JsString(email) => Some(email)
			case _ => None
		})

	private def envri: Future[Envri] = fetchOk("fetch envri", "/upload/envri")
		.flatMap(parseTo[Envri])

	private def authHost: Future[EnvriConfig] = fetchOk("fetch envri config", "/upload/envriconfig")
		.flatMap(parseTo[EnvriConfig])

	def fetchConfig: Future[InitAppInfo] = whoAmI.zip(envri).zip(authHost).map {
		case ((whoAmI, envri), authHost) => InitAppInfo(whoAmI, envri, authHost)
	}

	def submitterIds: Future[IndexedSeq[SubmitterProfile]] =
		fetchOk(
			"fetch the list of available submitter ids",
			"/upload/submitterids",
			new RequestInit{credentials = RequestCredentials.include}
		)
			.flatMap(parseTo[IndexedSeq[SubmitterProfile]])
			.flatMap{ s =>
				if(s.isEmpty)
					Future.failed(new Exception("""You are not authorized to upload data.
					Please contact us to if you would like to get the permission."""))
				else Future.successful(s)
			}

	def stationInfo(orgClass: Option[URI], producingOrg: Option[URI])(using Envri): Future[IndexedSeq[Station]] =
		sparqlSelect(stations(orgClass, producingOrg)).map(_.map(toStation))

	def getObjSpecs(using Envri): Future[IndexedSeq[ObjSpec]] =
		sparqlSelect(objSpecs).map(_.map(toObjSpec))

	def getSites(station: URI): Future[IndexedSeq[NamedUri]] =
		sparqlSelect(sites(station)).map(_.map(toSite)).map(disambiguateNames)

	def getSamplingPoints(site: URI): Future[IndexedSeq[SamplingPoint]] =
		sparqlSelect(samplingpoints(site)).map((_.map(toSamplingPoint)))

	def getL3SpatialCoverages(using Envri): Future[IndexedSeq[SpatialCoverage]] =
		envri.flatMap{ envri =>
			if(envri == Envri.SITES) Future.successful(IndexedSeq.empty)
			else sparqlSelect(l3spatialCoverages).map(_.map(toSpatialCoverage))
		}

	private def disambiguateNames(list: IndexedSeq[NamedUri]): IndexedSeq[NamedUri] =
		list.groupBy(_.name).valuesIterator.flatMap( g =>
			if (g.length <= 1) g
			else g.map{nUri =>
				val uriSegm = nUri.uri.getPath().split('/').last
				nUri.copy(name = s"${nUri.name} ($uriSegm)")
			}
		).toIndexedSeq.sortBy(_.name)

	def getPeople(using Envri): Future[IndexedSeq[NamedUri]] =
		sparqlSelect(people).map(_.map(toPerson)).map(disambiguateNames)

	def getOrganizations(using Envri): Future[IndexedSeq[NamedUri]] =
		sparqlSelect(organizations).map(_.map(toOrganization)).map(disambiguateNames)

	def getDatasetColumns(dataset: URI): Future[IndexedSeq[DatasetVar]] =
		sparqlSelect(datasetColumnQuery(dataset)).map(_.map(toDatasetVar))
	def getDatasetVariables(dataset: URI): Future[IndexedSeq[DatasetVar]] =
		sparqlSelect(datasetVariableQuery(dataset)).map(_.map(toDatasetVar))

	def tryIngestion(
		file: File, spec: ObjSpec, nRows: Option[Int], varnames: Option[Seq[String]]
	)(implicit envriConfig: EnvriConfig): Future[Unit] = {

		val firstVarName: Option[String] = varnames.flatMap(_.headOption).filter(_ => spec.isSpatiotemporal)

		if(spec.isStationTimeSer || firstVarName.isDefined){

			val nRowsQ = nRows.fold("")(nr => s"&nRows=$nr")
			val varsQ = varnames.fold(""){vns =>
				val varsJson = encodeURIComponent(Json.toJson(vns).toString)
				s"&varnames=$varsJson"
			}

			val url = s"https://${envriConfig.dataHost}/tryingest?specUri=${spec.uri}$nRowsQ$varsQ"
			fetchOk("validating data object", url, new RequestInit{
				body = file
				method = HttpMethod.PUT
			}).map(_ => ())
		} else Future.successful(())
	}

	def sparqlSelect(query: String): Future[IndexedSeq[Binding]] =
		fetchOk("execute a SPARQL query", "/sparql", new RequestInit{
			body = query
			method = HttpMethod.POST
		})
		.flatMap(parseTo[JsObject])
		.map(jsobj =>
			(jsobj \ "results" \ "bindings")
				.validate[JsArray]
				.map(_.value.collect(parseBinding))
				.get.toVector
		)

	def submitMetadata[T : Writes](dto: T): Future[URI] = {
		val json = Json.toJson(dto)
		fetchOk("upload metadata", "/upload", new RequestInit{
			method = HttpMethod.POST
			body = Json.prettyPrint(json)
			headers = Dictionary("Content-Type" -> "application/json")
			credentials = RequestCredentials.include
		})
		.flatMap(_.text().toFuture)
		.map(new URI(_))
	}

	def uploadFile(file: File, dataURL: URI): Future[String] =
		fetchOk("upload file", dataURL.toString, new RequestInit{
			method = HttpMethod.PUT
			body = file
			headers = Dictionary("Content-Type" -> "application/octet-stream")
			credentials = RequestCredentials.include
		})
		.flatMap(_.text())

	def getMetadata(uri: URI): Future[UploadDto] =
		fetchOk("fetch existing object", s"/dtodownload?uri=$uri")
			.flatMap(parseTo[UploadDto])

	def createDraftDoi(uri: URI): Future[Doi] =
		fetchOk("create draft DOI", "/dois/createDraft", new RequestInit{
			method = HttpMethod.POST
			body = Json.prettyPrint(Json.toJson(uri))
			headers = Dictionary("Content-Type" -> "application/json")
			credentials = RequestCredentials.include
		})
		.flatMap(parseTo[Doi])

	def getKeywordList(using envri: Envri): Future[IndexedSeq[String]] =
		if (envri == Envri.SITES) Future.successful(IndexedSeq.empty)
		else
			fetchOk("fetch keyword list", "/uploadgui/gcmdkeywords.json")
				.flatMap(parseTo[IndexedSeq[String]])

	private val parseBinding: PartialFunction[JsValue, Binding] = {
		case b: JsObject => b.fields.map{
			case (key, v) => key -> (v \ "value").validate[String].get
		}.toMap
	}

	private def parseTo[T : Reads](resp: Response): Future[T] =
		resp.text().map(Json.parse(_).as[T])

	private def fetchOk(errorHint: String, uri: String, reqInit: RequestInit = null): Future[Response] =
		fetch(uri, reqInit).flatMap(resp =>
			if resp.status >= 200 && resp.status < 300
			then Future.successful(resp)
			else resp.text().flatMap{errTxt =>
				val msg = s"Error when trying to $errorHint: $errTxt"
				Future.failed(new Exception(msg))
			}
		)
}
