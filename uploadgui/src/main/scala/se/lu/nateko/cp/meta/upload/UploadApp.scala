package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import org.scalajs.dom.html
import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.upload.formcomponents.{HtmlElements, ProgressBar}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.URIUtils
import scala.concurrent.Future
import scala.util.{ Success, Failure }
import java.net.URI
import se.lu.nateko.cp.meta.upload.formcomponents.Button
import se.lu.nateko.cp.meta.core.data

object UploadApp {
	import Utils.*
	import JsonSupport.given

	private val loginBlock = new HtmlElements("#login-block")
	private val formBlock = new HtmlElements("#form-block")
	private val headerButtons = new HtmlElements("#header-buttons-container")
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
		val gcmdKeywordsFut = Backend.getKeywordList

		val formFut = for(
			subms <- submsFut;
			objSpecs <- specsFut;
			spatCovs <- spatCovsFut;
			gcmdKeywords <- gcmdKeywordsFut
		) yield{
			implicit val bus = new PubSubBus
			new Form(subms, objSpecs, spatCovs, gcmdKeywords, upload _, createDoi _)
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

	private def upload(
		dto: UploadDto,
		file: Option[dom.File]
	)(implicit envri: Envri, envriConf: EnvriConfig): Future[URI] = whenDone{
		file match {
			case Some(file) =>
				Backend.submitMetadata(dto).flatMap(dataURL =>
					Backend.uploadFile(file, dataURL).map(_ => dataURL)
				)
			case None =>
				Backend.submitMetadata(dto)
		}
	}(dataURL => {
		progressBar.hide()
		val metaURL = new URI("https://" + envriConf.metaHost + dataURL.getPath())
		val doiCreation = if(envri == data.Envri.ICOS) " or create a draft DOI." else ""
		showAlert(s"Success! <a class='alert-link' href='${metaURL}'>View metadata</a>$doiCreation", "alert alert-success")
		val createDoiButton = new Button("new-doi-button", () => createDoi(metaURL))
		createDoiButton.enable()
	})

	private def createDoi(uri: URI): Unit = {
		whenDone{
			Backend.createDraftDoi(uri)
		}(doi => {
			showAlert(s"Draft DOI created: ${doi}. <a class='alert-link' target='_blank' href='https://doi.icos-cp.eu/?q=${doi}'>Edit or submit for publication</a>", "alert alert-success")
		}).onComplete {
				case _ => progressBar.hide()
			}
	}

	def whenDone[T](fut: Future[T])(cb: T => Unit): Future[T] = fut.andThen{
		case Success(res) => cb(res)
		case Failure(err) => {
			showAlert(err.getMessage, "alert alert-danger text-break")
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
