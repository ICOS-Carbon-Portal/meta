package se.lu.nateko.cp.meta.ingestion

import java.net.URI

import scala.concurrent.Future

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.rio.helpers.ContextStatementCollector
import org.eclipse.rdf4j.rio.turtle.TurtleParser

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers
import akka.stream.scaladsl.StreamConverters
import se.lu.nateko.cp.meta.utils.rdf4j.toRdf
import akka.stream.Materializer
import se.lu.nateko.cp.meta.api.CloseableIterator

class RemoteRdfGraphIngester(endpoint: URI, rdfGraph: URI)(implicit system: ActorSystem, m: Materializer) extends Ingester{

	import system.dispatcher

	override def getStatements(using factory: ValueFactory): Ingestion.Statements = {
		makeQuery().flatMap(
			resp => resp.status match {
				case StatusCodes.OK =>

					val inputStr = resp.entity.dataBytes.runWith(StreamConverters.asInputStream())
					val graphUri: IRI = rdfGraph.toRdf
					val collector = new ContextStatementCollector(factory, graphUri)
					val parser = new TurtleParser(factory)

					parser.setRDFHandler(collector)

					Future{
						parser.parse(inputStr, rdfGraph.toString)
						import scala.jdk.CollectionConverters.IteratorHasAsScala
						new CloseableIterator.Wrap(collector.getStatements.iterator().asScala, () => ())
					}
				case _ =>
					resp.discardEntityBytes()
					Future.failed(new Exception(s"Got ${resp.status} from the server"))
			}
		)
	}

	private def makeQuery(): Future[HttpResponse] = {
		Http().singleRequest(
			HttpRequest(
				method = HttpMethods.POST,
				uri = endpoint.toString,
				headers = headers.Accept(MediaTypes.`text/plain`) :: Nil,//expecting RDF Turtle in response
				entity = constructQuery
			)
		)
	}

	private def constructQuery: String = s"""
		|construct {?s ?p ?o}
		|from <$rdfGraph>
		|where {?s ?p ?o}""".stripMargin
}
