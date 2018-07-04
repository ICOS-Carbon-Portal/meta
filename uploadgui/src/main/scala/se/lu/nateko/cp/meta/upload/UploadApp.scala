package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import org.scalajs.dom.html
import play.api.libs.json.JsString

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils

object UploadApp {
	import Utils._

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
		val href = s"https://auth.fieldsites.se/login/?targetUrl=$url"
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
