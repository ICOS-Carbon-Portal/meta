package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import org.scalajs.dom.html
import se.lu.nateko.cp.meta.UploadMetadataDto

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils

object UploadApp {
	import Utils._

	def main(args: Array[String]): Unit = {

		whenDone(Backend.fetchConfig) {
			case InitAppInfo(Some(_), envri, _) =>
				implicit val envr = envri
				val form = new Form(upload _, subm => Backend.stationInfo(subm.producingOrganizationClass))
				displayForm()

				whenDone {
					Backend.getObjSpecs.map(_.filter(_.dataLevel == 0))
				}(form.objSpecSelect.setOptions)

				whenDone(Backend.submitterIds)(form.submitterIdSelect.setOptions)

			case InitAppInfo(None, _, envriConf) => displayLoginButton(envriConf.authHost)
		}
	}

	def displayLoginButton(authHost: String): Unit = {
		val url = URIUtils.encodeURI(dom.window.location.href)
		val href = s"https://$authHost/login/?targetUrl=$url"
		getElement[html.Anchor]("login-button").get.setAttribute("href", href)
		getElement[html.Div]("login-block").get.style.display = "block"
	}

	def displayForm(): Unit = {
		getElement[html.Div]("login-block").get.style.display = "none"
		getElement[html.Form]("form-block").get.style.display = "block"
	}

	def upload(dto: UploadMetadataDto, file: dom.File): Unit = {
		whenDone{
			Backend.submitMetadata(dto).flatMap(uri => Backend.uploadFile(file, uri))
		}(doi => {
			showAlert(s"Data uploaded! <a class='alert-link' href='https://doi.org/$doi'>View metadata</a>", "alert alert-success")
		})
	}
}
