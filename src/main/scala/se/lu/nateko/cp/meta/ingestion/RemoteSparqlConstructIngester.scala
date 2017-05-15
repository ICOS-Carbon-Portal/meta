package se.lu.nateko.cp.meta.ingestion

import java.net.{URI => JavaUri}

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import org.openrdf.model.Statement
import org.openrdf.model.URI
import org.openrdf.model.ValueFactory
import org.openrdf.rio.helpers.ContextStatementCollector
import org.openrdf.rio.turtle.TurtleParser

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import se.lu.nateko.cp.meta.utils.sesame.javaUriToSesame

class RemoteRdfGraphIngester(endpoint: JavaUri, rdfGraph: JavaUri)(implicit system: ActorSystem) extends Ingester{

	implicit private val materializer = ActorMaterializer()
	import system.dispatcher

	override def getStatements(factory: ValueFactory): Iterator[Statement] = {
		val statementsFut: Future[Iterator[Statement]] =
			makeQuery().flatMap(
				resp => resp.status match {
					case StatusCodes.OK =>

						val inputStr = resp.entity.dataBytes.runWith(StreamConverters.asInputStream())
						val graphUri: URI = javaUriToSesame(rdfGraph)(factory)
						val collector = new ContextStatementCollector(factory, graphUri)
						val parser = new TurtleParser(factory)

						parser.setRDFHandler(collector)

						Future{
							parser.parse(inputStr, rdfGraph.toString)
							import scala.collection.JavaConverters.asScalaIteratorConverter
							collector.getStatements.iterator().asScala
						}
					case _ =>
						resp.discardEntityBytes()
						Future.failed(new Exception(s"Got ${resp.status} from the server"))
				}
			)
		Await.result(statementsFut, 10 seconds)
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
