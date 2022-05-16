package se.lu.nateko.cp.meta.services.linkeddata

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.eclipse.rdf4j.model.Namespace
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.impl.SimpleNamespace
import org.eclipse.rdf4j.model.vocabulary.{ OWL, RDF, RDFS, XSD }
import org.eclipse.rdf4j.rio.RDFWriterFactory
import org.eclipse.rdf4j.rio.rdfxml.RDFXMLWriterFactory
import org.eclipse.rdf4j.rio.turtle.TurtleWriterFactory

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
import akka.stream.scaladsl.StreamConverters
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import scala.util.Using

object InstanceServerSerializer {

	private val utf8 = HttpCharsets.`UTF-8`

	val turtleContType = getContType("text/turtle", ".ttl")
	val xmlContType = getContType("application/rdf+xml", ".rdf")
	private val basicNamespaces = Seq(XSD.NS, OWL.NS, RDFS.NS, RDF.NS)


	private val statementProdMarshaller: ToResponseMarshaller[StatementProducer] = Marshaller(
		implicit exeCtxt => producer => Future.successful(
			getMarshalling(producer, ContentTypes.`text/plain(UTF-8)`, turtleContType, new TurtleWriterFactory()) ::
			getMarshalling(producer, turtleContType, turtleContType, new TurtleWriterFactory()) ::
			getMarshalling(producer, ContentType(MediaTypes.`application/xml`, utf8), xmlContType, new RDFXMLWriterFactory()) ::
			getMarshalling(producer, xmlContType, xmlContType, new RDFXMLWriterFactory()) ::
			Nil
		)
	)

	val marshaller: ToResponseMarshaller[InstanceServer] = statementProdMarshaller
		.compose(is => new StatementProducer{
			def statements = is.getStatements(None, None, None)
			def namespaces = {
				val ns = new SimpleNamespace("", is.writeContexts.head.stringValue)

				val readNss = is.readContexts.diff(is.writeContexts).map{uri =>
					val prefix = uri.stringValue.stripSuffix("/").split('/').last
					new SimpleNamespace(prefix, uri.stringValue)
				}

				ns +: (basicNamespaces ++ readNss)
			}
		})

	val statementIterMarshaller: ToResponseMarshaller[() => CloseableIterator[Statement]] = statementProdMarshaller
		.compose(stMaker => new StatementProducer{
			def statements = stMaker()
			def namespaces = basicNamespaces
		})

	private def getContType(name: String, filenameExt: String): ContentType = {
		val mediaType = MediaType.custom(name, false, fileExtensions = List(filenameExt))
		ContentType(mediaType, () => utf8)
	}

	private def getMarshalling(
		producer: StatementProducer,
		acceptedContType: ContentType,
		returnedContType: ContentType,
		writerFactory: RDFWriterFactory
	)(implicit ctxt: ExecutionContext) = Marshalling.WithFixedContentType(acceptedContType, () => {

		val entityBytes = StreamConverters.asOutputStream().mapMaterializedValue{ outStr =>
			ctxt.execute(() =>
				Using.Manager{use =>
					val statements = use(producer.statements)
					use.acquire(outStr)
					val rdfWriter = writerFactory.getWriter(outStr)
					rdfWriter.startRDF()
					producer.namespaces.foreach(ns => rdfWriter.handleNamespace(ns.getPrefix, ns.getName))
					statements.foreach(rdfWriter.handleStatement)
					rdfWriter.endRDF()
				}
				.failed.foreach(ctxt.reportFailure)
			)
		}
		HttpResponse(entity = HttpEntity(returnedContType, entityBytes))
	})

	private trait StatementProducer{
		def statements: CloseableIterator[Statement]
		def namespaces: Iterable[Namespace]
	}
}
