package se.lu.nateko.cp.meta.instanceserver

import akka.stream.scaladsl.Source
import akka.NotUsed
import akka.util.ByteString
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import scala.concurrent.Future
import akka.http.scaladsl.marshalling.Marshalling
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.model.HttpResponse

object InstanceServerSerializer {

	private val mediaType = MediaType.custom("application/rdf+xml", false, fileExtensions = List("rdf"))
	private val contType = ContentType(mediaType, () => HttpCharsets.`UTF-8`)

	def marshaller: ToResponseMarshaller[InstanceServer] = Marshaller(
		implicit exeCtxt => server => Future.successful(
			List(
				Marshalling.WithFixedContentType(
					contType,
					() => getResponse(server)
				)
			)
		)
	)

	private def serialize(server: InstanceServer): Source[ByteString, NotUsed] = {
		???
	}

	private def getResponse(server: InstanceServer): HttpResponse = {
		???
	}

}