package se.lu.nateko.cp.meta.services.upload

import java.net.URI

import akka.http.scaladsl.marshalling.Marshalling._
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller, ToResponseMarshaller}
import akka.http.scaladsl.model._
import play.twirl.api.Html
import se.lu.nateko.cp.meta.api.{CitationClient, Doi, StatisticsClient}
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.JsonSupport._
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, StaticCollection}
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.views.LandingPageExtras
import se.lu.nateko.cp.meta.utils.getStackTrace
import spray.json._
import views.html.{CollectionLandingPage, LandingPage, MessagePage}

import scala.concurrent.{ExecutionContext, Future}
import se.lu.nateko.cp.meta.core.data.StaticObject

class PageContentMarshalling(handleService: URI, citer: CitationClient, vocab: CpVocab, statisticsClient: StatisticsClient) {

	import PageContentMarshalling.{getHtml, getJson}

	implicit def staticObjectMarshaller(implicit envri: Envri): ToResponseMarshaller[() => Option[StaticObject]] = {
		import statisticsClient.executionContext
		val template: StaticObject => Future[Option[String] => Html] = obj =>
			for(
				dlCount <- statisticsClient.getDownloadCount(obj.hash);
				previewCount <- statisticsClient.getPreviewCount(obj.hash)
			) yield (citOpt: Option[String]) => {
				val extras = LandingPageExtras(citOpt, dlCount, previewCount)
				LandingPage(obj, extras, handleService, vocab)
			}
		makeMarshaller(template, MessagePage("Data object not found", ""), _.doi)
	}

	implicit def statCollMarshaller(implicit envri: Envri, conf: EnvriConfig): ToResponseMarshaller[() => Option[StaticCollection]] =
		makeMarshaller(
			coll => Future.successful(CollectionLandingPage(coll, _)),
			MessagePage("Collection not found", ""),
			_.doi
		)

	private def makeMarshaller[T: JsonWriter](
		templateFetcher: T => Future[Option[String] => Html],
		notFoundPage: => Html,
		toDoi: T => Option[String]
	): ToResponseMarshaller[() => Option[T]] = {

		def fetchCitationOpt(implicit dataItemOpt: Option[T], ctxt: ExecutionContext): Future[Option[String]] =
			dataItemOpt.flatMap(toDoi).flatMap(Doi.unapply) match {
				case None => Future.successful(None)
				case Some(doi) => citer.getCitation(doi).map(Some(_)).recover {
					case err: Throwable =>
						Some("Error fetching the citation from DataCite: " + err.getMessage)
				}
			}

		def fetchHtmlMaker(citOpt: Option[String])(implicit dataItemOpt: Option[T], ctxt: ExecutionContext): Future[HttpCharset => HttpResponse] = dataItemOpt match {
			case Some(obj) =>
				templateFetcher(obj).map { template =>
					val html = template(citOpt)
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
				citOpt <- fetchCitationOpt;
				htmlMaker <- fetchHtmlMaker(citOpt)
			) yield List(
				WithOpenCharset(MediaTypes.`text/html`, htmlMaker),
				WithFixedContentType(ContentTypes.`application/json`, () => getJson(dataItemOpt))
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

	def getJson[T: JsonWriter](dataItemOpt: Option[T]) =
		dataItemOpt match {
			case Some(obj) => HttpResponse(
				entity = HttpEntity(ContentTypes.`application/json`, obj.toJson.prettyPrint)
			)
			case None => HttpResponse(StatusCodes.NotFound)
		}

	def errorMarshaller(implicit envri: Envri): ToEntityMarshaller[Throwable] = Marshaller(
		_ => err => {

			val msg = (if(err.getMessage == null) "" else err.getMessage) + "\n" + getStackTrace(err)

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
