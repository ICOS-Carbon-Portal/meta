package se.lu.nateko.cp.meta.keyword

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging

import java.net.URI
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

/**
 * Standalone, one-shot tool for the two keyword passes over the Virtuoso triplestore. It
 * boots its own actor system, runs the single pass its command asks for and then terminates.
 *
 * One command per pass:
 *
 *   - `split [--delete-has-keywords]` runs [[KeywordSplitter.splitAll]], a migration that
 *     expands the legacy comma-separated `hasKeywords` literals into singular
 *     `hasOwnKeyword` triples on the very same subject and graph. By default the pass is
 *     purely additive: the `hasKeywords` literals are left in place next to their new
 *     singular counterparts. Given `--delete-has-keywords` it also removes them,
 *     destructively but conservatively: a `hasKeywords` triple is deleted only after its
 *     singular counterparts have been written to the same graph and read back from the
 *     store, so an interrupted or partially failing run can simply be repeated. Subjects
 *     whose `hasKeywords` holds no parseable keyword are only warned about: re-running would
 *     not change them.
 *
 *   - `materialize` runs [[KeywordMaterializer.materializeAll]], which rebuilds the derived
 *     `hasKeyword` triples out of the `hasOwnKeyword` triples the split produced. Its
 *     derived graph is a cache, cleared and rebuilt on every run, so this pass is meant to
 *     be repeated; re-running it on a schedule (e.g. via cron) is left to the deployment.
 *
 * Either command exits non-zero if its pass failed, and the split also if any subject's
 * singular triples could not be confirmed or any `hasKeywords` survived its deletion, so a
 * deployment can tell a clean run from one needing a repeat.
 *
 * Note that Virtuoso's instance graphs are rebuilt from the Postgres RDF log by
 * [[se.lu.nateko.cp.meta.cli.TriplestorePopulator]], which clears and re-uploads each graph.
 * A repopulation therefore undoes the split; making it permanent requires the same change in
 * the RDF log. The materialized graph is not in the RDF log at all, and is simply rebuilt by
 * the next `materialize`.
 */
object KeywordsApp {

	private val SplitCommand = "split"
	private val MaterializeCommand = "materialize"
	private val DeleteFlag = "--delete-has-keywords"
	private val Usage =
		s"""Usage: KeywordsApp <command>
			|  $SplitCommand [$DeleteFlag]
			|      expand hasKeywords literals into hasOwnKeyword triples
			|  $MaterializeCommand
			|      rebuild the derived hasKeyword graph out of hasOwnKeyword triples""".stripMargin

	private val DerivedGraph = URI.create("http://meta.icos-cp.eu/derived/keywords/")

	/** The pass a command line asks for, named for the log. */
	private enum Pass(val name: String) {
		case Split(deleteSourceTriples: Boolean) extends Pass("splitting")
		case Materialize extends Pass("materialization")
	}

	def main(args: Array[String]): Unit = {
		val pass = parse(args) match {
			case Some(pass) => pass
			case None =>
				System.err.println(Usage)
				System.exit(2)
				return
		}

		val host = sys.props.getOrElse("virtuoso.host", sys.env.getOrElse("VIRTUOSO_HOST", "http://localhost:8890"))
		val username = sys.props.getOrElse("virtuoso.username", sys.env.getOrElse("VIRTUOSO_USERNAME", "dummy"))
		val password = sys.props.getOrElse("virtuoso.password", sys.env.getOrElse("VIRTUOSO_PASSWORD", "dummy"))

		given system: ActorSystem = ActorSystem("keywords")
		given ExecutionContext = system.dispatcher
		val log = Logging.getLogger(system, this)

		val repo = new StandaloneVirtuosoRepository(host, username, password)

		val exitCode = try {
			val clean = pass match {
				case Pass.Split(deleteSourceTriples) =>
					log.info(
						s"Keyword splitting run started against $host, " +
							s"hasKeywords deletion ${if (deleteSourceTriples) "on" else "off"}"
					)
					val summary = new KeywordSplitter(repo, deleteSourceTriples).splitAll()
					log.info(s"Keyword splitting done: $summary")
					summary.unconfirmed.isEmpty && summary.deletionFailures.isEmpty

				case Pass.Materialize =>
					log.info(s"Keyword materialization run started against $host, graph $DerivedGraph")
					val materializer = new KeywordMaterializer(repo, DerivedGraph)
					// the materializer is asynchronous, but this tool has nothing to do until
					// its single pass is over
					val written = Await.result(materializer.materializeAll(), Duration.Inf)
					log.info(s"Keyword materialization done, $written triples in derived graph")
					true
			}
			if (clean) 0 else 1
		} catch {
			case error: Throwable =>
				log.error(error, s"Keyword ${pass.name} failed")
				1
		} finally {
			repo.shutDown()
			system.terminate()
		}

		System.exit(exitCode)
	}

	private def parse(args: Array[String]): Option[Pass] = args.toIndexedSeq match {
		case Seq(SplitCommand) => Some(Pass.Split(deleteSourceTriples = false))
		case Seq(SplitCommand, DeleteFlag) => Some(Pass.Split(deleteSourceTriples = true))
		case Seq(MaterializeCommand) => Some(Pass.Materialize)
		case other =>
			System.err.println(
				if (other.isEmpty) "No command given"
				else s"Unrecognized arguments: ${other.mkString(" ")}"
			)
			None
	}
}
