package se.lu.nateko.cp.meta.services.upload

import scala.language.unsafeNulls

import akka.http.scaladsl.marshalling.Marshalling.*
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.*
import eu.icoscp.envri.Envri
import play.twirl.api.Html
import se.lu.nateko.cp.meta.core.CommonJsonSupport.WithErrors
import se.lu.nateko.cp.meta.core.HandleProxiesConfig
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, StaticCollection, StaticObject}
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.utils.{Validated, getStackTrace}
import se.lu.nateko.cp.meta.views.LandingPageExtras
import spray.json.*
import views.html.{CollectionLandingPage, LandingPage, MessagePage}

import java.util.concurrent.ExecutionException
import scala.concurrent.{ExecutionContext, Future}

class PageContentMarshalling(handleProxies: HandleProxiesConfig):

	import PageContentMarshalling.*

	def staticObjectMarshaller (using Envri, EnvriConfig, CpVocab) : ToResponseMarshaller[() => Validated[StaticObject]] =
		val template: PageTemplate[StaticObject] = (obj, errors) =>
			LandingPage(obj, LandingPageExtras(errors), handleProxies)
		makeMarshaller(template, messagePage("Data object not found", _))


	def staticCollectionMarshaller(using Envri, EnvriConfig): ToResponseMarshaller[() => Validated[StaticCollection]] =
		val template: PageTemplate[StaticCollection] = (coll, errors) =>
			CollectionLandingPage(coll, LandingPageExtras(errors), handleProxies)
		makeMarshaller(template, messagePage("Collection not found", _))

	def staticObjectAsyncMarshaller (using Envri, EnvriConfig, CpVocab) : ToResponseMarshaller[() => Future[Validated[StaticObject]]] =
		val template: PageTemplate[StaticObject] = (obj, errors) =>
			LandingPage(obj, LandingPageExtras(errors), handleProxies)
		makeAsyncMarshaller(template, messagePage("Data object not found", _))

	def staticCollectionAsyncMarshaller(using Envri, EnvriConfig): ToResponseMarshaller[() => Future[Validated[StaticCollection]]] =
		val template: PageTemplate[StaticCollection] = (coll, errors) =>
			CollectionLandingPage(coll, LandingPageExtras(errors), handleProxies)
		makeAsyncMarshaller(template, messagePage("Collection not found", _))


	private def makeMarshaller[T: JsonWriter](
		template: PageTemplate[T],
		notFoundPage: ErrorList => Html,
	): ToResponseMarshaller[() => Validated[T]] =
		Marshaller {_ => producer =>
			val itemV: Validated[T] = producer()
			Future.successful(marshallings(itemV, template, notFoundPage))
		}

	private def makeAsyncMarshaller[T: JsonWriter](
		template: PageTemplate[T],
		notFoundPage: ErrorList => Html,
	): ToResponseMarshaller[() => Future[Validated[T]]] =
		Marshaller { exeCtxt => producer =>
			given ExecutionContext = exeCtxt
			producer().map(marshallings(_, template, notFoundPage))
		}

	private def marshallings[T: JsonWriter](
		itemV: Validated[T], template: PageTemplate[T], notFoundPage: ErrorList => Html
	): List[Marshalling[HttpResponse]] = List(
		WithOpenCharset(MediaTypes.`text/html`, htmlMaker(itemV, template, notFoundPage)),
		WithFixedContentType(ContentTypes.`application/json`, () => getJson(itemV))
	)

	private def htmlMaker[T](
		itemV: Validated[T], template: PageTemplate[T], notFoundPage: ErrorList => Html
	)(charset: HttpCharset): HttpResponse = itemV.result match
		case Some(item) => HttpResponse(entity = getHtml(template(item, itemV.errors), charset))
		case None => HttpResponse(StatusCodes.NotFound, entity = getHtml(notFoundPage(itemV.errors), charset))
end PageContentMarshalling

object PageContentMarshalling:

	type ErrorList = Seq[String]
	type PageTemplate[T] = (T, ErrorList) => Html

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
