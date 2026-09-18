package se.lu.nateko.cp.meta.services.upload

import scala.language.unsafeNulls

import akka.http.scaladsl.marshalling.Marshalling.*
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.*
import eu.icoscp.envri.Envri
import play.twirl.api.Html
import se.lu.nateko.cp.meta.core.CommonJsonSupport.WithErrors
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, StaticCollection, StaticObject}
import se.lu.nateko.cp.meta.services.linkeddata.{LandingPage, LandingPageAssembler, LandingPageRenderer}
import se.lu.nateko.cp.meta.utils.{Validated, getStackTrace}
import spray.json.*
import views.html.MessagePage

import java.util.concurrent.ExecutionException
import scala.concurrent.{ExecutionContext, Future}

class PageContentMarshalling(
	landingPages: LandingPageAssembler,
	renderer: LandingPageRenderer
):

	import PageContentMarshalling.*

	def staticObjectAsyncMarshaller(using Envri, EnvriConfig): ToResponseMarshaller[() => Future[Validated[StaticObject]]] =
		makeLandingPageAsyncMarshaller(
			landingPages.staticObject,
			messagePage("Data object not found", _)
		)

	def staticCollectionAsyncMarshaller(using Envri, EnvriConfig): ToResponseMarshaller[() => Future[Validated[StaticCollection]]] =
		makeLandingPageAsyncMarshaller(
			landingPages.staticCollection,
			messagePage("Collection not found", _)
		)


	// TODO Either allow fetching JSON without looking up download/preview stats, or include the stats in the JSON
	private def makeLandingPageAsyncMarshaller[T: JsonWriter, P <: LandingPage](
		assemble: (T, ErrorList) => Future[P],
		notFoundPage: ErrorList => Html,
	)(using Envri, EnvriConfig): ToResponseMarshaller[() => Future[Validated[T]]] =
		Marshaller { exeCtxt => producer =>
			given ExecutionContext = exeCtxt
			producer().flatMap: itemV =>
				fetchHtmlMaker(itemV, assemble, notFoundPage).map: htmlMaker =>
					List(
						WithOpenCharset(MediaTypes.`text/html`, htmlMaker),
						WithFixedContentType(ContentTypes.`application/json`, () => getJson(itemV))
					)
		}

	private def fetchHtmlMaker[T, P <: LandingPage](
		itemV: Validated[T], assemble: (T, ErrorList) => Future[P], notFoundPage: ErrorList => Html
	)(using ExecutionContext, Envri, EnvriConfig): Future[HttpCharset => HttpResponse] = itemV.result match
		case Some(item) => assemble(item, itemV.errors).map: page =>
			val html = renderer.render(page)
			charset => HttpResponse(entity = getHtml(html, charset))
		case None => Future.successful: charset =>
			HttpResponse(StatusCodes.NotFound, entity = getHtml(notFoundPage(itemV.errors), charset))
end PageContentMarshalling

object PageContentMarshalling:

	type ErrorList = Seq[String]
	given twirlHtmlEntityMarshaller: ToEntityMarshaller[Html] = Marshaller(
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

	def messagePage(title: String, errors: ErrorList)(using Envri, EnvriConfig) =
		MessagePage(title, errors.mkString("\n"))

	def getJson[T: JsonWriter](itemV: Validated[T]): HttpResponse = itemV.result match
		case Some(obj) =>
			val js = WithErrors(obj, itemV.errors).toJson
			HttpResponse(
				entity = HttpEntity(ContentTypes.`application/json`, js.prettyPrint)
			)
		case None =>
			if itemV.errors.isEmpty then HttpResponse(StatusCodes.NotFound)
			else
				HttpResponse(
					status = StatusCodes.InternalServerError,
					entity = getText(itemV.errors.mkString("\n"), HttpCharsets.`UTF-8`)
				)


	def errorMarshaller(using Envri, EnvriConfig): ToEntityMarshaller[Throwable] = Marshaller(
		_ => err => {

			val msg = extractMessage(err)

			val getErrorPage: HttpCharset => MessageEntity = getHtml(MessagePage("Server error", msg), _)

			Future.successful(
				WithOpenCharset(MediaTypes.`text/plain`, getText(msg, _)) ::
				WithOpenCharset(MediaTypes.`text/html`, getErrorPage) ::
				Opaque(() => getText(msg, HttpCharsets.`UTF-8`)) ::
				Nil
			)
		}
	)

	private def extractMessage(err: Throwable): String = err match {
		case boxed: ExecutionException if (boxed.getCause != null) =>
			extractMessage(boxed.getCause)
		case _ =>
			(if(err.getMessage == null) "" else err.getMessage) + "\n" + getStackTrace(err)
	}

end PageContentMarshalling
