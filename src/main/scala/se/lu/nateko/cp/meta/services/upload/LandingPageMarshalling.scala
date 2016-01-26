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

object LandingPageMarshalling {

	private def getHtml(dataObj: DataObject, charset: HttpCharset) = HttpResponse(
		entity = HttpEntity(
			ContentType.WithCharset(MediaTypes.`text/html`, charset),
			views.html.LandingPage(dataObj).body
		)
	)

	private def getJson(dataObj: DataObject) = HttpResponse(
		entity = HttpEntity(ContentTypes.`application/json`, dataObj.toJson.prettyPrint)
	)

	def marshaller: ToResponseMarshaller[DataObject] = Marshaller(
		implicit exeCtxt => dataObj => Future.successful(
			WithOpenCharset(MediaTypes.`text/html`, getHtml(dataObj, _)) ::
			WithFixedContentType(ContentTypes.`application/json`, () => getJson(dataObj)) :: Nil
		)
	)

}