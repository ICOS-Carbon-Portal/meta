package se.lu.nateko.cp.meta.services.upload

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.Marshalling._
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model._

import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.JsonSupport._

import scala.concurrent.Future
import spray.json._
import play.twirl.api.Html

object PageContentMarshalling {

	val twirlHtmlMarshaller: ToResponseMarshaller[Html] = Marshaller(
		implicit exeCtxt => html => Future.successful(
			WithOpenCharset(MediaTypes.`text/html`, getHtml(html, _)) :: Nil
		)
	)

	implicit val dataObjectMarshaller: ToResponseMarshaller[() => Option[DataObject]] = Marshaller(
		implicit exeCtxt => dataObjGetter => Future.successful(
			WithOpenCharset(MediaTypes.`text/html`, getHtml(dataObjGetter, _)) ::
			WithFixedContentType(ContentTypes.`application/json`, () => getJson(dataObjGetter)) :: Nil
		)
	)

	private def getHtml(html: Html, charset: HttpCharset) = HttpResponse(
		entity = HttpEntity(
			ContentType.WithCharset(MediaTypes.`text/html`, charset),
			html.body
		)
	)

	private def getHtml(dataObjGetter: () => Option[DataObject], charset: HttpCharset) =
		dataObjGetter() match {
			case Some(dataObj) => HttpResponse(
				entity = HttpEntity(
					ContentType.WithCharset(MediaTypes.`text/html`, charset),
					views.html.LandingPage(dataObj).body
				)
			)
			case None => HttpResponse(StatusCodes.NotFound)
		}

	private def getJson(dataObjGetter: () => Option[DataObject]) =
		dataObjGetter() match {
			case Some(dataObj) => HttpResponse(
				entity = HttpEntity(ContentTypes.`application/json`, dataObj.toJson.prettyPrint)
			)
			case None => HttpResponse(StatusCodes.NotFound)
		}

}
