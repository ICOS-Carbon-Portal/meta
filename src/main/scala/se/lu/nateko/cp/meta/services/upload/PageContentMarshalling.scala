package se.lu.nateko.cp.meta.services.upload

import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToResponseMarshaller, ToEntityMarshaller}
import akka.http.scaladsl.marshalling.Marshalling._
import akka.http.scaladsl.model._
import se.lu.nateko.cp.meta.api.Doi
import se.lu.nateko.cp.meta.core.data.{DataObject, EnvriConfig, StaticCollection}
import se.lu.nateko.cp.meta.core.data.JsonSupport._
import se.lu.nateko.cp.meta.services.CpVocab

import scala.concurrent.Future
import spray.json._
import play.twirl.api.Html
import java.net.URI

import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.api.CitationClient
import views.html.{LandingPage, MessagePage, CollectionLandingPage}

class PageContentMarshalling(handleService: URI, citer: CitationClient, vocab: CpVocab) {

	import PageContentMarshalling.{getHtml, getJson}

	implicit def dataObjectMarshaller(implicit envri: Envri): ToResponseMarshaller[() => Option[DataObject]] =
		makeMarshaller(
			LandingPage(_, _, handleService, vocab),
			MessagePage("Data object not found", ""),
			_.doi
		)

	implicit def statCollMarshaller(implicit envri: Envri, conf: EnvriConfig): ToResponseMarshaller[() => Option[StaticCollection]] =
		makeMarshaller(
			CollectionLandingPage(_, _),
			MessagePage("Collection not found", ""),
			_.doi
		)

	private def makeMarshaller[T: JsonWriter](
		template: (T, Option[String]) => Html,
		notFoundPage: => Html,
		toDoi: T => Option[String]
	): ToResponseMarshaller[() => Option[T]] = Marshaller{ implicit exeCtxt => producer =>

		Future(producer()).flatMap{dataItemOpt =>

			val doiOpt: Option[Doi] = dataItemOpt.flatMap(toDoi).flatMap(Doi.unapply)

			val citationOptFut: Future[Option[String]] = doiOpt match{
				case None => Future.successful(None)
				case Some(doi) => citer.getCitation(doi).map(Some(_)).recover{
					case err: Throwable =>
						Some("Error fetching the citation from DataCite: " + err.getMessage)
				}
			}

			citationOptFut.map{citOpt =>

				val makeHtml: HttpCharset => HttpResponse = charset => dataItemOpt match {
					case Some(obj) =>
						HttpResponse(entity = getHtml(template(obj, citOpt), charset))
					case None =>
						HttpResponse(StatusCodes.NotFound, entity = getHtml(notFoundPage, charset))
				}

				WithOpenCharset(MediaTypes.`text/html`, makeHtml) ::
				WithFixedContentType(ContentTypes.`application/json`, () => getJson(dataItemOpt)) ::
				Nil
			}
		}
	}

}

object PageContentMarshalling{

	implicit val twirlHtmlEntityMarshaller: ToEntityMarshaller[Html] = Marshaller(
		_ => html => Future.successful(
			WithOpenCharset(MediaTypes.`text/html`, getHtml(html, _)) :: Nil
		)
	)

	def twirlStatusHtmlMarshalling(fetcher: () => (StatusCode, Html)): Marshalling[HttpResponse] =
		WithOpenCharset(
			MediaTypes.`text/html`,
			charset => {
				val (status, html) = fetcher()
				HttpResponse(status, entity = getHtml(html, charset))
			}
		)

	private def getHtml(html: Html, charset: HttpCharset) = HttpEntity(
		ContentType.WithCharset(MediaTypes.`text/html`, charset),
		html.body
	)

	private def getText(content: String, charset: HttpCharset) = HttpEntity(
		ContentType.WithCharset(MediaTypes.`text/plain`, charset),
		content
	)

	def getJson[T: JsonWriter](dataItemOpt: Option[T]) =
		dataItemOpt match {
			case Some(obj) => HttpResponse(
				entity = HttpEntity(ContentTypes.`application/json`, obj.toJson.prettyPrint)
			)
			case None => HttpResponse(StatusCodes.NotFound)
		}

	def errorMarshaller(implicit envri: Envri): ToEntityMarshaller[Throwable] = Marshaller(
		_ => err => {

			val msg = {
				val traceWriter = new java.io.StringWriter()
				err.printStackTrace(new java.io.PrintWriter(traceWriter))
				(if(err.getMessage == null) "" else err.getMessage) + "\n" + traceWriter.toString
			}

			val getErrorPage: HttpCharset => MessageEntity = getHtml(MessagePage("Server error", msg), _)

			Future.successful(
				WithOpenCharset(MediaTypes.`text/plain`, getText(msg, _)) ::
				WithOpenCharset(MediaTypes.`text/html`, getErrorPage) ::
				Opaque(() => getText(msg, HttpCharsets.`UTF-8`)) ::
				Nil
			)
		}
	)
}
