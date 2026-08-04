package se.lu.nateko.cp.meta.keyword

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.{BasicCredentialsProvider, CloseableHttpClient, HttpClients}
import org.eclipse.rdf4j.http.client.SPARQLProtocolSession
import org.eclipse.rdf4j.query.resultio.TupleQueryResultFormat
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository


/**
 * Standalone, one-shot tool that splits `hasKeywords` into singular `hasOwnKeyword` triples
 * throughout the Virtuoso triplestore. It boots its own actor system, runs a single
 * [[KeywordSplitter.splitAll]] pass and then terminates.
 *
 * By default the pass is purely additive: the `hasKeywords` literals are left in place next
 * to their new singular counterparts. Given `--delete-has-keywords` it also removes them,
 * destructively but conservatively: a `hasKeywords` triple is deleted only after its
 * singular counterparts have been written to the same graph and read back from the store, so
 * an interrupted or partially failing run can simply be repeated. It exits non-zero if any
	 * subject's singular triples could not be confirmed, so a deployment can tell a clean run
	 * from one needing a repeat. Deletion survivors are also reported as failures. Subjects
	 * whose `hasKeywords` holds no parseable keyword are only warned about: re-running would
	 * not change them.
 *
 * Note that Virtuoso's instance graphs are rebuilt from the Postgres RDF log by
 * [[se.lu.nateko.cp.meta.cli.TriplestorePopulator]], which clears and re-uploads each graph.
 * A repopulation therefore undoes this migration; making it permanent requires the same
 * change in the RDF log.
 */
object KeywordSplittingApp {

	private val DeleteFlag = "--delete-has-keywords"
	private val Usage = s"Usage: KeywordSplittingApp [$DeleteFlag]"

	def main(args: Array[String]): Unit = {
		val deleteSourceTriples = args.toIndexedSeq match {
			case Seq() => false
			case Seq(DeleteFlag) => true
			case other =>
				System.err.println(s"Unrecognized arguments: ${other.mkString(" ")}\n$Usage")
				System.exit(2)
				return
		}

		val host = sys.props.getOrElse("virtuoso.host", sys.env.getOrElse("VIRTUOSO_HOST", "http://localhost:8890"))
		val username = sys.props.getOrElse("virtuoso.username", sys.env.getOrElse("VIRTUOSO_USERNAME", "dummy"))
		val password = sys.props.getOrElse("virtuoso.password", sys.env.getOrElse("VIRTUOSO_PASSWORD", "dummy"))

		given system: ActorSystem = ActorSystem("keywordSplitter")
		val log = Logging.getLogger(system, this)

		val repo = new StandaloneVirtuosoRepository(host, username, password)

		val splitter = new KeywordSplitter(repo, deleteSourceTriples)

		log.info(
			s"Keyword splitting run started against $host, " +
				s"hasKeywords deletion ${if (deleteSourceTriples) "on" else "off"}"
		)
		val exitCode = try {
			val summary = splitter.splitAll()
			log.info(s"Keyword splitting done: $summary")
			if (summary.unconfirmed.isEmpty && summary.deletionFailures.isEmpty) 0 else 1
		} catch {
			case error: Throwable =>
				log.error(error, "Keyword splitting failed")
				1
		} finally {
			repo.shutDown()
			system.terminate()
		}

		System.exit(exitCode)
		}
}

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
