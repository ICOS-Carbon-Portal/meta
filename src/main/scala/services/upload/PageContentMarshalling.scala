package se.lu.nateko.cp.meta.services.upload

import scala.language.unsafeNulls

import akka.http.scaladsl.marshalling.Marshalling.*
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.*
import eu.icoscp.envri.Envri
import play.twirl.api.Html
import se.lu.nateko.cp.meta.api.StatisticsClient
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

class PageContentMarshalling(handleProxies: HandleProxiesConfig, statisticsClient: StatisticsClient):

	import PageContentMarshalling.*

	def staticObjectMarshaller (using Envri, EnvriConfig, CpVocab) : ToResponseMarshaller[() => Validated[StaticObject]] =
		import statisticsClient.executionContext
		val template: PageTemplate[StaticObject] = (obj, errors) =>
			for(
				dlCount <- statisticsClient.getObjDownloadCount(obj);
				previewCount <- statisticsClient.getPreviewCount(obj.hash)
			) yield {
				val extras = LandingPageExtras(dlCount, previewCount, errors)
				LandingPage(obj, extras, handleProxies)
			}
		makeMarshaller(template, messagePage("Data object not found", _))


	def staticCollectionMarshaller(using Envri, EnvriConfig): ToResponseMarshaller[() => Validated[StaticCollection]] =
		import statisticsClient.executionContext
		val template: PageTemplate[StaticCollection] = (coll, errors) =>
			for(dlCount <- statisticsClient.getCollDownloadCount(coll.res))
			yield {
				val extras = LandingPageExtras(dlCount, None, errors)
				CollectionLandingPage(coll, extras, handleProxies)
			}
		makeMarshaller(template, messagePage("Collection not found", _))


	// TODO Either allow fetching JSON without looking up download/preview stats, or include the stats in the JSON
	private def makeMarshaller[T: JsonWriter](
		templateFetcher: (T, ErrorList) => Future[Html],
		notFoundPage: ErrorList => Html,
	): ToResponseMarshaller[() => Validated[T]] = {

		def fetchHtmlMaker(itemV: Validated[T])(using ExecutionContext): Future[HttpCharset => HttpResponse] = itemV.result match
			case Some(item) =>
				templateFetcher(item, itemV.errors).map: html =>
					charset => HttpResponse(entity = getHtml(html, charset))

			case None =>
				val status = if itemV.errors.isEmpty then StatusCodes.NotFound else StatusCodes.InternalServerError
				Future.successful:
					charset => HttpResponse(status, entity = getHtml(notFoundPage(itemV.errors), charset))


		Marshaller {exeCtxt => producer =>
			given ExecutionContext = exeCtxt
			val itemV: Validated[T] = producer()
			for (
				htmlMaker <- fetchHtmlMaker(itemV)
			) yield List(
				WithOpenCharset(MediaTypes.`text/html`, htmlMaker),
				WithFixedContentType(ContentTypes.`application/json`, () => getJson(itemV))
			)
		}
	}
end PageContentMarshalling

object PageContentMarshalling:

	type ErrorList = Seq[String]
	type PageTemplate[T] = (T, ErrorList) => Future[Html]

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
