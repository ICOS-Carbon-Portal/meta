package se.lu.nateko.cp.meta.services.upload

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.Marshalling._
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model._

import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.core.data.JsonSupport._

import scala.concurrent.Future
import spray.json._
import play.twirl.api.Html
import java.net.URI

class PageContentMarshalling(handleService: URI) {

	implicit val dataObjectMarshaller: ToResponseMarshaller[() => Option[DataObject]] =
		makeMarshaller(views.html.LandingPage(_, handleService))

	implicit val statCollMarshaller: ToResponseMarshaller[() => Option[StaticCollection]] =
		makeMarshaller(views.html.CollectionLandingPage(_))

	private def makeMarshaller[T: JsonFormat](template: T => Html): ToResponseMarshaller[() => Option[T]] = Marshaller(
		implicit exeCtxt => producer => Future.successful(
			WithOpenCharset(MediaTypes.`text/html`, getHtml(producer, template, _)) ::
			WithFixedContentType(ContentTypes.`application/json`, () => getJson(producer)) :: Nil
		)
	)

	private def getHtml[T](producer: () => Option[T], template: T => Html, charset: HttpCharset) =
		producer() match {
			case Some(obj) => HttpResponse(
				entity = HttpEntity(
					ContentType.WithCharset(MediaTypes.`text/html`, charset),
					template(obj).body
				)
			)
			case None => HttpResponse(StatusCodes.NotFound)
		}

	private def getJson[T: JsonFormat](producer: () => Option[T]) =
		producer() match {
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
