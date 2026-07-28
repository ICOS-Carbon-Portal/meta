package se.lu.nateko.cp.meta.keyword

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import se.lu.nateko.cp.cpauth.core.ConfigLoader.appConfig
import se.lu.nateko.cp.meta.{ConfigLoader, CpmetaConfig}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.VirtuosoRepository


/**
 * Standalone, one-shot tool that replaces `hasKeywords` with singular `hasKeyword` triples
 * throughout the Virtuoso triplestore. It boots its own actor system, runs a single
 * [[KeywordSplitter.splitAll]] pass and then terminates.
 *
 * The pass is destructive but conservative: a `hasKeywords` triple is deleted only after its
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

	def main(args: Array[String]): Unit = {
		val config: CpmetaConfig = ConfigLoader.default

		given system: ActorSystem = ActorSystem("keywordSplitter", config = appConfig)
		val log = Logging.getLogger(system, this)

		val repo = new VirtuosoRepository(config.virtuoso)
		val vocab = new CpmetaVocab(repo.getValueFactory)

		val splitter = new KeywordSplitter(repo, vocab)

		log.info(s"Keyword splitting run started against ${config.virtuoso.host}")
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
