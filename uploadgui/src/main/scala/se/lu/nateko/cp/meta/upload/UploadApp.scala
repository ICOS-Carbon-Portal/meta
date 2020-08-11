package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import org.scalajs.dom.html
import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.upload.formcomponents.{HtmlElements, ProgressBar}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils
import scala.concurrent.Future
import scala.util.{ Success, Try, Failure }

object UploadApp {
	import Utils._
	import JsonSupport._

	private val loginBlock = new HtmlElements("#login-block")
	private val formBlock = new HtmlElements("#form-block")
	private val headerButtons = new HtmlElements("#header-buttons")
	val progressBar = new ProgressBar("#progress-bar")
	private val alert = getElementById[html.Div]("alert-placeholder").get

	def main(args: Array[String]): Unit = whenDone(Backend.fetchConfig) {
		case InitAppInfo(Some(_), envri, envriConf) => setupForm(envri, envriConf)
		case InitAppInfo(None, _, envriConf) => displayLoginButton(envriConf.authHost)
	}

	private def setupForm(implicit envri: Envri, envriConf: EnvriConfig) = {
		val submsFut = Backend.submitterIds
		val specsFut = Backend.getObjSpecs
		val spatCovsFut = Backend.getL3SpatialCoverages

		val formFut = for(subms <- submsFut; objSpecs <- specsFut; spatCovs <- spatCovsFut) yield{
			implicit val bus = new PubSubBus
			new Form(subms, objSpecs, spatCovs, upload _)
		}

		whenDone(formFut){ _ =>
			loginBlock.hide()
			formBlock.show()
			headerButtons.show()
		}
	}

	private def displayLoginButton(authHost: String): Unit = {
		val url = URIUtils.encodeURI(dom.window.location.href)
		val href = s"https://$authHost/login/?targetUrl=$url"
		getElementById[html.Anchor]("login-button").get.setAttribute("href", href)
		loginBlock.show()
	}

	private def upload(dto: UploadDto, file: Option[dom.File]): Unit = file match {
		case Some(file) => {
			whenDone{
				Backend.submitMetadata(dto).flatMap(uri => Backend.uploadFile(file, uri))
			}(pid => {
				showAlert(s"${file.name} uploaded! <a class='alert-link' href='https://hdl.handle.net/$pid'>View metadata</a>", "alert alert-success")
			}).onComplete {
				case _ => progressBar.hide()
			}
		}
		case None => {
			whenDone{
				Backend.submitMetadata(dto)
			}(dataURL => {
				showAlert(s"Metadata uploaded! <a class='alert-link' href='${dataURL.getPath}'>View metadata</a>", "alert alert-success")
				progressBar.hide()
			})
		}
	}

	def whenDone[T](fut: Future[T])(cb: T => Unit): Future[T] = fut.andThen{
		case Success(res) => cb(res)
		case Failure(err) => {
			showAlert(err.getMessage, "alert alert-danger")
			progressBar.hide()
		}
	}

	def showAlert(message: String, alertType: String): Unit = {
		alert.setAttribute("class", alertType)
		alert.innerHTML = message
	}

	def hideAlert(): Unit = {
		alert.setAttribute("class", "")
		alert.innerHTML = ""
	}

}
