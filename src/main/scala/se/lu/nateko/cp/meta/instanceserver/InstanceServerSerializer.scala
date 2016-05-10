package se.lu.nateko.cp.meta.instanceserver

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.openrdf.rio.rdfxml.RDFXMLWriterFactory

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.Marshalling
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaType
import se.lu.nateko.cp.meta.utils.streams.OutputStreamWriterSource

object InstanceServerSerializer {

	private val mediaType = MediaType.custom("application/rdf+xml", false, fileExtensions = List("rdf"))
	private val contType = ContentType(mediaType, () => HttpCharsets.`UTF-8`)

	def marshaller: ToResponseMarshaller[InstanceServer] = Marshaller(
		implicit exeCtxt => server => Future.successful(
			List(
				Marshalling.WithFixedContentType(
					ContentTypes.`text/xml(UTF-8)`,
					() => getResponse(server)
				)
			)
		)
	)

	private def getResponse(server: InstanceServer)(implicit ctxt: ExecutionContext): HttpResponse = {
		val entityBytes = OutputStreamWriterSource{ outStr =>
			val rdfWriter = new RDFXMLWriterFactory().getWriter(outStr)
			rdfWriter.startRDF()
			server.getStatements(None, None, None).foreach(rdfWriter.handleStatement)
			rdfWriter.endRDF()
			outStr.close()
		}
		HttpResponse(entity = HttpEntity(contType, entityBytes))
	}

}
