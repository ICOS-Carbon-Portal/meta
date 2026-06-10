package se.lu.nateko.cp.meta.keyword

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import se.lu.nateko.cp.cpauth.core.ConfigLoader.appConfig
import se.lu.nateko.cp.meta.{ConfigLoader, CpmetaConfig}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.VirtuosoRepository

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

/**
 * Standalone service that keeps the derived `hasKeyword` triples in the Virtuoso
 * triplestore up to date. It boots its own actor system and runs a continuous
 * [[KeywordMaterializationWorker]] against the configured triplestore.
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
		val interval = conf.refreshIntervalMinutes.minutes

		val materializer = new KeywordMaterializer(repo, vocab, conf.derivedGraph)
		system.actorOf(KeywordMaterializationWorker.props(materializer, interval), "keyword-materialization-worker")

		log.info(
			s"Keyword materialization service started against ${config.virtuoso.host}; " +
			s"refreshing graph ${conf.derivedGraph} every ${conf.refreshIntervalMinutes} min"
		)

		sys.addShutdownHook:
			log.info("Shutting down keyword materialization service")
			repo.shutDown()
			system.terminate()

end KeywordMaterializationApp
