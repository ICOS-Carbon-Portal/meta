package se.lu.nateko.cp.meta.services.upload

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.Marshalling._
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model._

import se.lu.nateko.cp.meta.api.Doi
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.core.data.JsonSupport._

import scala.concurrent.Future
import spray.json._
import play.twirl.api.Html
import java.net.URI
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.api.CitationClient

class PageContentMarshalling(handleService: URI, citer: CitationClient) {

	implicit def dataObjectMarshaller(implicit envri: Envri): ToResponseMarshaller[() => Option[DataObject]] =
		makeMarshaller(views.html.LandingPage(_, _, handleService), _.doi)

	implicit def statCollMarshaller(implicit envri: Envri): ToResponseMarshaller[() => Option[StaticCollection]] =
		makeMarshaller(views.html.CollectionLandingPage(_, _, handleService), _.doi)

	private def makeMarshaller[T: JsonFormat](
		template: (T, Option[String]) => Html,
		toDoi: T => Option[String]
	): ToResponseMarshaller[() => Option[T]] = Marshaller{ implicit exeCtxt => producer =>

		Future(producer()).flatMap{dataItemOpt =>

			val doiOpt: Option[Doi] = dataItemOpt.flatMap(toDoi).flatMap(Doi.unapply)

			val citationOptFut: Future[Option[String]] = doiOpt match{
				case None => Future.successful(None)
				case Some(doi) => citer.getCitation(doi).map(Some(_)).recover{
					case _: Throwable =>
						Some("Error fetching the citation from DataCite")
				}
			}

			citationOptFut.map{citOpt =>
				WithOpenCharset(MediaTypes.`text/html`, getHtml[T](dataItemOpt, template(_, citOpt), _)) ::
				WithFixedContentType(ContentTypes.`application/json`, () => getJson(dataItemOpt)) :: Nil
			}
		}
	}

	private def getHtml[T](dataItemOpt: Option[T], template: T => Html, charset: HttpCharset) =
		dataItemOpt match {
			case Some(obj) => HttpResponse(
				entity = HttpEntity(
					ContentType.WithCharset(MediaTypes.`text/html`, charset),
					template(obj).body
				)
			)
			case None => HttpResponse(StatusCodes.NotFound)
		}

	private def getJson[T: JsonFormat](dataItemOpt: Option[T]) =
		dataItemOpt match {
			case Some(obj) => HttpResponse(
				entity = HttpEntity(ContentTypes.`application/json`, obj.toJson.prettyPrint)
			)
			case None => HttpResponse(StatusCodes.NotFound)
		}

}

object PageContentMarshalling{

	val twirlHtmlMarshaller: ToResponseMarshaller[Html] = Marshaller(
		implicit exeCtxt => html => Future.successful(
			WithOpenCharset(MediaTypes.`text/html`, getHtml(html, _)) :: Nil
		)
	)

	private def getHtml(html: Html, charset: HttpCharset) = HttpResponse(
		entity = HttpEntity(
			ContentType.WithCharset(MediaTypes.`text/html`, charset),
			html.body
		)
	)
}
