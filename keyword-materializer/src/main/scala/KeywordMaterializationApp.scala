package se.lu.nateko.cp.meta.keyword

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.{BasicCredentialsProvider, CloseableHttpClient, HttpClients}
import org.eclipse.rdf4j.http.client.SPARQLProtocolSession
import org.eclipse.rdf4j.query.resultio.TupleQueryResultFormat
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository

import java.net.URI
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * Standalone, one-shot tool that rebuilds the derived `hasKeyword` triples in the
 * Virtuoso triplestore. It boots its own actor system, runs a single
 * [[KeywordMaterializer.materializeAll]] pass and then terminates. Re-running it on a
 * schedule (e.g. via cron) is left to the deployment.
 */
object KeywordMaterializationApp:

	def main(args: Array[String]): Unit =
		val host = sys.props.getOrElse("virtuoso.host", sys.env.getOrElse("VIRTUOSO_HOST", "http://localhost:8890"))
		val username = sys.props.getOrElse("virtuoso.username", sys.env.getOrElse("VIRTUOSO_USERNAME", "dummy"))
		val password = sys.props.getOrElse("virtuoso.password", sys.env.getOrElse("VIRTUOSO_PASSWORD", "dummy"))

		given system: ActorSystem = ActorSystem("keywordMaterializer")
		given ExecutionContext = system.dispatcher
		val log = Logging.getLogger(system, this)

		val repo = new StandaloneVirtuosoRepository(host, username, password)
		val derivedGraph = URI.create("http://meta.icos-cp.eu/derived/keywords/")

		val materializer = new KeywordMaterializer(repo, derivedGraph)

		log.info(s"Keyword materialization run started against $host, graph $derivedGraph")

		materializer.materializeAll().onComplete: outcome =>
			outcome match
				case Success(written) =>
					log.info(s"Keyword materialization done, $written triples in derived graph")
				case Failure(error) =>
					log.error(error, "Keyword materialization failed")

			repo.shutDown()
			system.terminate()
			system.registerOnTermination:
				System.exit(if outcome.isSuccess then 0 else 1)

end KeywordMaterializationApp

private final class StandaloneVirtuosoRepository(host: String, username: String, password: String)
		extends SPARQLRepository(s"$host/sparql", s"$host/sparql-auth"):

	private val httpClient: CloseableHttpClient =
		val credentials = new BasicCredentialsProvider()
		credentials.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))
		HttpClients.custom().setDefaultCredentialsProvider(credentials).setMaxConnPerRoute(32).setMaxConnTotal(64).build()

	setHttpClient(httpClient)
	super.init()

	override def createSPARQLProtocolSession(): SPARQLProtocolSession =
		val session = super.createSPARQLProtocolSession()
		session.setPreferredTupleQueryResultFormat(TupleQueryResultFormat.JSON)
		session

	override protected def shutDownInternal(): Unit =
		try super.shutDownInternal()
		finally httpClient.close()
