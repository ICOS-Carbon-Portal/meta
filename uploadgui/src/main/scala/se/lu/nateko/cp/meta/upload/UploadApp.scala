package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import org.scalajs.dom.html
import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils

object UploadApp {
	import Utils._

	def main(args: Array[String]): Unit = {

		whenDone(Backend.fetchConfig) {
			case InitAppInfo(Some(_), envri, envriConf) => setupForm(envri, envriConf)
			case InitAppInfo(None, _, envriConf) => displayLoginButton(envriConf.authHost)
		}
	}

	private def setupForm(envri: Envri, envriConf: EnvriConfig) = {
		implicit val envr = envri
		implicit val envrConf = envriConf
		val form = new Form(upload _, subm => Backend.stationInfo(subm.producingOrganizationClass, subm.producingOrganization))
		displayForm()

		whenDone(Backend.submitterIds)(form.submitterIdSelect.setOptions)
	}

	def displayLoginButton(authHost: String): Unit = {
		val url = URIUtils.encodeURI(dom.window.location.href)
		val href = s"https://$authHost/login/?targetUrl=$url"
		getElementById[html.Anchor]("login-button").get.setAttribute("href", href)
		getElementById[html.Div]("login-block").get.style.display = "block"
	}

	def displayForm(): Unit = {
		getElementById[html.Div]("login-block").get.style.display = "none"
		getElementById[html.Form]("form-block").get.style.display = "block"
	}

	def upload(dto: UploadDto, file: Option[dom.File]): Unit = file match {
		case Some(file) => {
			whenDone{
				getElementById[html.Div]("progress-bar").get.style.display = "block"
				Backend.submitMetadata(dto).flatMap(uri => Backend.uploadFile(file, uri))
			}(pid => {
				showAlert(s"${file.name} uploaded! <a class='alert-link' href='https://hdl.handle.net/$pid'>View metadata</a>", "alert alert-success")
			}).onComplete {
				case _ => getElementById[html.Div]("progress-bar").get.style.display = "none"
			}
		}
		case None => {
			whenDone{
				Backend.submitMetadata(dto)
			}(metadataURL => {
				showAlert(s"Metadata uploaded! <a class='alert-link' href='$metadataURL'>View metadata</a>", "alert alert-success")
			})
		}
	}
}
