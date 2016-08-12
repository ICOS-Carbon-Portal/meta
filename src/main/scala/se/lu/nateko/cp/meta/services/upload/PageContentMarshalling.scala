package se.lu.nateko.cp.meta.services.upload

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.Marshalling._
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model._

import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.JsonSupport._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import spray.json._
import play.twirl.api.Html

object PageContentMarshalling {

	def twirlHtmlMarshaller: ToResponseMarshaller[Html] = Marshaller(
		implicit exeCtxt => html => Future.successful(
			WithOpenCharset(MediaTypes.`text/html`, getHtml(html, _)) :: Nil
		)
	)

	def dataObjectMarshaller: ToResponseMarshaller[DataObject] = Marshaller(
		implicit exeCtxt => dataObj => Future.successful(
			WithOpenCharset(MediaTypes.`text/html`, getHtml(dataObj, _)) ::
			WithFixedContentType(ContentTypes.`application/json`, () => getJson(dataObj)) :: Nil
		)
	)

	private def getHtml(html: Html, charset: HttpCharset) = HttpResponse(
		entity = HttpEntity(
			ContentType.WithCharset(MediaTypes.`text/html`, charset),
			html.body
		)
	)

	private def getHtml(dataObj: DataObject, charset: HttpCharset) = HttpResponse(
		entity = HttpEntity(
			ContentType.WithCharset(MediaTypes.`text/html`, charset),
			views.html.LandingPage(dataObj).body
		)
	)

	private def getJson(dataObj: DataObject) = HttpResponse(
		entity = HttpEntity(ContentTypes.`application/json`, dataObj.toJson.prettyPrint)
	)

}
