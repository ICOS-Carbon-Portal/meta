package se.lu.nateko.cp.meta.keyword

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import se.lu.nateko.cp.cpauth.core.ConfigLoader.appConfig
import se.lu.nateko.cp.meta.{ConfigLoader, CpmetaConfig}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.VirtuosoRepository


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

		val config: CpmetaConfig = ConfigLoader.default

		given system: ActorSystem = ActorSystem("keywordSplitter", config = appConfig)
		val log = Logging.getLogger(system, this)

		val repo = new VirtuosoRepository(config.virtuoso)
		val vocab = new CpmetaVocab(repo.getValueFactory)

		val splitter = new KeywordSplitter(repo, vocab, deleteSourceTriples)

		log.info(
			s"Keyword splitting run started against ${config.virtuoso.host}, " +
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
