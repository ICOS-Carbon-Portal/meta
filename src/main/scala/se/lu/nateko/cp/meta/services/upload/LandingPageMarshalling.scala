package se.lu.nateko.cp.meta.services.upload

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.Marshalling._
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model._

import se.lu.nateko.cp.meta.core.data.DataPackage
import se.lu.nateko.cp.meta.services.LandingPageBuilder.getPage
import se.lu.nateko.cp.meta.core.data.JsonSupport._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import spray.json._

object LandingPageMarshalling {

	private def getHtml(dataObj: DataPackage, charset: HttpCharset) = HttpResponse(
		entity = HttpEntity(
			ContentType.WithCharset(MediaTypes.`text/html`, charset),
			getPage(dataObj)
		)
	)

	private def getJson(dataObj: DataPackage) = HttpResponse(
		entity = HttpEntity(ContentTypes.`application/json`, dataObj.toJson.prettyPrint)
	)

	def marshaller: ToResponseMarshaller[DataPackage] = Marshaller(
		implicit exeCtxt => dataObj => Future.successful(
			WithFixedContentType(ContentTypes.`application/json`, () => getJson(dataObj)) ::
			WithOpenCharset(MediaTypes.`text/html`, getHtml(dataObj, _)) :: Nil
		)
	)

}