package se.lu.nateko.cp.meta.services.linkeddata

import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToResponseMarshaller}
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpCharsets, HttpEntity, HttpResponse, MediaType, MediaTypes}
import akka.stream.StreamDetachedException
import akka.stream.scaladsl.StreamConverters
import org.eclipse.rdf4j.model.impl.SimpleNamespace
import org.eclipse.rdf4j.model.vocabulary.{ OWL, RDF, RDFS, XSD }
import org.eclipse.rdf4j.model.{Namespace, Statement}
import org.eclipse.rdf4j.rio.RDFWriterFactory
import org.eclipse.rdf4j.rio.rdfxml.RDFXMLWriterFactory
import org.eclipse.rdf4j.rio.turtle.TurtleWriterFactory
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.InstanceServer

import java.io.IOException
import scala.concurrent.{ExecutionContext, Future}
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
				val ns = new SimpleNamespace("", is.writeContext.stringValue)

				val readNss = is.readContexts.diff(Seq(is.writeContext)).map{uri =>
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
					try
						producer.namespaces.foreach(ns => rdfWriter.handleNamespace(ns.getPrefix, ns.getName))
						statements.foreach(rdfWriter.handleStatement)
					finally
						rdfWriter.endRDF()
				}
				.failed.filter{//swallow the exception raised if entityBytes' consumer cancels
					case io: IOException =>
						io.getCause match
							case _: StreamDetachedException => false
							case _ => true
					case _ => true
				}.foreach(ctxt.reportFailure)
			)
		}
		HttpResponse(entity = HttpEntity(returnedContType, entityBytes))
	})

	private trait StatementProducer{
		def statements: CloseableIterator[Statement]
		def namespaces: Iterable[Namespace]
	}
}
