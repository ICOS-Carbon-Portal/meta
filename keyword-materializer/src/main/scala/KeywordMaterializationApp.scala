package se.lu.nateko.cp.meta.keyword

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import se.lu.nateko.cp.cpauth.core.ConfigLoader.appConfig
import se.lu.nateko.cp.meta.{ConfigLoader, CpmetaConfig}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.VirtuosoRepository

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
		val config: CpmetaConfig = ConfigLoader.default

		given system: ActorSystem = ActorSystem("keywordMaterializer", config = appConfig)
		given ExecutionContext = system.dispatcher
		val log = Logging.getLogger(system, this)

		val repo = new VirtuosoRepository(config.virtuoso)
		val vocab = new CpmetaVocab(repo.getValueFactory)
		val conf = config.keywordMaterialization

		val materializer = new KeywordMaterializer(repo, vocab, conf.derivedGraph)

		log.info(s"Keyword materialization run started against ${config.virtuoso.host}, graph ${conf.derivedGraph}")

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
