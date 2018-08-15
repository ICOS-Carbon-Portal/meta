package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import org.scalajs.dom.html
import play.api.libs.json.JsString

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils
import se.lu.nateko.cp.meta.core.data._

object UploadApp {
	import Utils._

	implicit private val envri: Envri.Envri = if (dom.window.location.host.contains("fieldsites.se")) Envri.SITES else Envri.ICOS
	val form = new Form(upload)

	def main(args: Array[String]): Unit = {

		whenDone(Backend.whoAmI) {
			case JsString(_) =>
				displayForm()

				whenDone(Backend.sitesStationInfo)(form.stationSelect.setOptions)

				whenDone {
					Backend.getSitesObjSpecs.map(_.filter(_.dataLevel == 0))
				}(form.objSpecSelect.setOptions)

				whenDone(Backend.submitterIds)(form.submitterIdSelect.setOptions)

			case _ => displayLoginButton()
		}
	}

	def displayLoginButton(): Unit = {
		val url = URIUtils.encodeURI(dom.window.location.href)
		val authHost = if (envri == Envri.SITES) "auth.fieldsites.se" else "cpauth.icos-cp.eu"
		val href = s"https://$authHost/login/?targetUrl=$url"
		getElement[html.Anchor]("login-button").get.setAttribute("href", href)
		getElement[html.Div]("login-block").get.style.display = "block"
	}

	def displayForm(): Unit = {
		getElement[html.Div]("login-block").get.style.display = "none"
		getElement[html.Form]("form-block").get.style.display = "block"
	}

	def upload(): Unit = for(dto <- form.dto; file <- form.fileInput.file){
		whenDone{
			Backend.submitMetadata(dto).flatMap(uri => Backend.uploadFile(file, uri))
		}(doi => {
			showAlert(s"Data uploaded! View metadata: https://doi.org/$doi", "alert alert-success")
		})
	}
}
