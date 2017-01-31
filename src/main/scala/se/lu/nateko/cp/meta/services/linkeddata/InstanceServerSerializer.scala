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
import se.lu.nateko.cp.meta.api.CloseableIterator
import org.openrdf.model.Statement
import org.openrdf.model.vocabulary.{ OWL, RDF, RDFS, XMLSchema }
import org.openrdf.model.impl.NamespaceImpl

object InstanceServerSerializer {

	private val utf8 = HttpCharsets.`UTF-8`

	val turtleContType = getContType("text/turtle")
	val xmlContType = getContType("application/rdf+xml")
	private val basicNamespaces = Seq(XMLSchema.NS, OWL.NS, RDFS.NS, RDF.NS)


	private val statementProdMarshaller: ToResponseMarshaller[StatementProducer] = Marshaller(
		implicit exeCtxt => producer => Future.successful(
			Marshalling.WithFixedContentType(
				ContentTypes.`text/plain(UTF-8)`,
				() => getResponse(producer, turtleContType, new TurtleWriterFactory())
			) ::
			Marshalling.WithFixedContentType(
				ContentType(MediaTypes.`application/xml`, utf8),
				() => getResponse(producer, xmlContType, new RDFXMLWriterFactory())
			) :: Nil
		)
	)

	val marshaller: ToResponseMarshaller[InstanceServer] = statementProdMarshaller
		.compose(is => new StatementProducer{
			def statements = is.getStatements(None, None, None)
			def namespaces = {
				val ns = new NamespaceImpl("", is.writeContexts.head.stringValue)

				val readNss = is.readContexts.diff(is.writeContexts).map{uri =>
					val prefix = uri.stringValue.stripSuffix("/").split('/').last
					new NamespaceImpl(prefix, uri.stringValue)
				}

				ns +: (basicNamespaces ++ readNss)
			}
		})

	val statementIterMarshaller: ToResponseMarshaller[() => CloseableIterator[Statement]] = statementProdMarshaller
		.compose(stMaker => new StatementProducer{
			def statements = stMaker()
			def namespaces = basicNamespaces
		})

	private def getContType(name: String): ContentType = {
		val mediaType = MediaType.custom(name, false, fileExtensions = List(".rdf"))
		ContentType(mediaType, () => utf8)
	}

	private def getResponse(producer: StatementProducer, contType: ContentType, writerFactory: RDFWriterFactory)(implicit ctxt: ExecutionContext): HttpResponse = {
		val entityBytes = OutputStreamWriterSource{ outStr =>
			val statements = producer.statements
			try{
				val rdfWriter = writerFactory.getWriter(outStr)
				rdfWriter.startRDF()
				producer.namespaces.foreach(ns => rdfWriter.handleNamespace(ns.getPrefix, ns.getName))
				statements.foreach(rdfWriter.handleStatement)
				rdfWriter.endRDF()
			}finally{
				outStr.close()
				statements.close()
			}
		}
		HttpResponse(entity = HttpEntity(contType, entityBytes))
	}

	private trait StatementProducer{
		def statements: CloseableIterator[Statement]
		def namespaces: Iterable[Namespace]
	}
}
