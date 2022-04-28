package se.lu.nateko.cp.meta.services.upload

import java.net.URI
import java.util.concurrent.ExecutionException

import akka.http.scaladsl.marshalling.Marshalling.*
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.*
import play.twirl.api.Html
import se.lu.nateko.cp.meta.api.StatisticsClient
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, StaticCollection}
import se.lu.nateko.cp.meta.core.HandleProxiesConfig
import se.lu.nateko.cp.meta.services.citation.{Doi, CitationClient}
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.views.LandingPageExtras
import se.lu.nateko.cp.meta.utils.getStackTrace
import spray.json.*
import views.html.{CollectionLandingPage, LandingPage, MessagePage}

import scala.concurrent.{ExecutionContext, Future}
import se.lu.nateko.cp.meta.core.data.StaticObject
import scala.util.Failure
import scala.util.Try
import scala.util.Success

class PageContentMarshalling(handleProxies: HandleProxiesConfig, statisticsClient: StatisticsClient) {

	import PageContentMarshalling.{getHtml, getJson}

	implicit def staticObjectMarshaller(implicit envri: Envri, conf: EnvriConfig): ToResponseMarshaller[() => Option[StaticObject]] = {
		import statisticsClient.executionContext
		val template: StaticObject => Future[Html] = obj =>
			for(
				dlCount <- statisticsClient.getObjDownloadCount(obj);
				previewCount <- statisticsClient.getPreviewCount(obj.hash)
			) yield {
				val extras = LandingPageExtras(dlCount, previewCount)
				LandingPage(obj, extras, handleProxies)
			}
		makeMarshaller(template, MessagePage("Data object not found", ""))
	}

	implicit def statCollMarshaller(implicit envri: Envri, conf: EnvriConfig): ToResponseMarshaller[() => Option[StaticCollection]] = {
		import statisticsClient.executionContext
		val template: StaticCollection => Future[Html] = coll =>
			for(dlCount <- statisticsClient.getCollDownloadCount(coll.res))
			yield {
				val extras = LandingPageExtras(dlCount, None)
				CollectionLandingPage(coll, extras, handleProxies)
			}
		makeMarshaller(template, MessagePage("Collection not found", ""))
	}

	private def makeMarshaller[T: JsonWriter](
		templateFetcher: T => Future[Html],
		notFoundPage: => Html,
	): ToResponseMarshaller[() => Option[T]] = {

		def fetchHtmlMaker()(implicit dataItemOpt: Option[T], ctxt: ExecutionContext): Future[HttpCharset => HttpResponse] = dataItemOpt match {
			case Some(obj) =>
				templateFetcher(obj).map { html =>
					charset => HttpResponse(entity = getHtml(html, charset))
				}

			case None =>
				Future.successful(
					charset => HttpResponse(StatusCodes.NotFound, entity = getHtml(notFoundPage, charset))
				)
		}

		Marshaller {implicit exeCtxt => producer =>
			implicit val dataItemOpt: Option[T] = producer()
			for (
				htmlMaker <- fetchHtmlMaker()
			) yield List(
				WithOpenCharset(MediaTypes.`text/html`, htmlMaker),
				WithFixedContentType(ContentTypes.`application/json`, () => getJson(Success(dataItemOpt)))
			)
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

	def getJson[T: JsonWriter](dataItemOpt: Try[Option[T]]): HttpResponse = dataItemOpt match {
		case Success(Some(obj)) => HttpResponse(
			entity = HttpEntity(ContentTypes.`application/json`, obj.toJson.prettyPrint)
		)
		case Success(None) => HttpResponse(StatusCodes.NotFound)
		case Failure(err) => HttpResponse(
			status = StatusCodes.InternalServerError,
			entity = getText(err.getMessage, HttpCharsets.`UTF-8`)
		)
	}

	def errorMarshaller(using envri: Envri, conf: EnvriConfig): ToEntityMarshaller[Throwable] = Marshaller(
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
}
