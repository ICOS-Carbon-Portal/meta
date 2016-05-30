package se.lu.nateko.cp.meta.services.linkeddata

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.openrdf.model.Namespace
import org.openrdf.rio.RDFWriterFactory
import org.openrdf.rio.rdfxml.RDFXMLWriterFactory
import org.openrdf.rio.turtle.TurtleWriterFactory
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.Marshalling
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.MediaTypes
import se.lu.nateko.cp.meta.utils.streams.OutputStreamWriterSource
import se.lu.nateko.cp.meta.instanceserver.InstanceServer

object InstanceServerSerializer {

	private val utf8 = HttpCharsets.`UTF-8`

	val turtleContType = getContType("text/turtle")
	val xmlContType = getContType("application/rdf+xml")

	def marshaller: ToResponseMarshaller[InstanceServer] = Marshaller(
		implicit exeCtxt => server => Future.successful(
			Marshalling.WithFixedContentType(
				ContentTypes.`text/plain(UTF-8)`,
				() => getResponse(server, turtleContType, new TurtleWriterFactory())
			) ::
			Marshalling.WithFixedContentType(
				ContentType(MediaTypes.`application/xml`, utf8),
				() => getResponse(server, xmlContType, new RDFXMLWriterFactory())
			) :: Nil
		)
	)

	private def getContType(name: String): ContentType = {
		val mediaType = MediaType.custom(name, false, fileExtensions = List(".rdf"))
		ContentType(mediaType, () => utf8)
	}

	private def getResponse(server: InstanceServer, contType: ContentType, writerFactory: RDFWriterFactory)(implicit ctxt: ExecutionContext): HttpResponse = {
		val entityBytes = OutputStreamWriterSource{ outStr =>
			val statements = server.getStatements(None, None, None)
			try{
				val rdfWriter = writerFactory.getWriter(outStr)
				rdfWriter.startRDF()
				getNamespaces(server).foreach(ns => rdfWriter.handleNamespace(ns.getPrefix, ns.getName))
				statements.foreach(rdfWriter.handleStatement)
				rdfWriter.endRDF()
			}finally{
				outStr.close()
				statements.close()
			}
		}
		HttpResponse(entity = HttpEntity(contType, entityBytes))
	}

	private def getNamespaces(server: InstanceServer): Iterable[Namespace] = {
		import org.openrdf.model.impl.NamespaceImpl
		import org.openrdf.model.vocabulary.{ OWL, RDF, RDFS, XMLSchema }

		val ns = new NamespaceImpl("", server.writeContexts.head.stringValue)

		val readNss = server.readContexts.diff(server.writeContexts).map{uri =>
			val prefix = uri.stringValue.stripSuffix("/").split('/').last
			new NamespaceImpl(prefix, uri.stringValue)
		}

		Seq(ns, XMLSchema.NS, OWL.NS, RDFS.NS, RDF.NS) ++ readNss
	}

}
