package se.lu.nateko.cp.meta

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.Materializer
import se.lu.nateko.cp.cpauth.core.ConfigLoader.appConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.metaflow.MetaFlow
import se.lu.nateko.cp.meta.routes.MainRoute
import se.lu.nateko.cp.meta.services.citation.CitationClient.{readCitCache, readDoiCache}
import se.lu.nateko.cp.meta.services.sparql.magic.IndexHandler
import se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

object Main extends App with CpmetaJsonProtocol{

	given system: ActorSystem = ActorSystem("cpmeta", config = appConfig)
	private val log = Logging.getLogger(system, this)
	private given ExecutionContext = system.dispatcher

	val config: CpmetaConfig = ConfigLoader.default
	given EnvriConfigs = config.core.envriConfigs
	val metaFactory = new MetaDbFactory

	val optIndexDataFut: Future[Option[IndexData]] =
		import config.{rdfStorage => conf}
		val recreateIndex: Boolean = conf.recreateAtStartup || conf.recreateCpIndexAtStartup
		if(recreateIndex) IndexHandler.dropStorage()
		if(recreateIndex || conf.disableCpIndex) Future.successful(None)
		else {
			log.info("Trying to restore SPARQL magic index...")
			val indexDataFut = IndexHandler.restore()
			indexDataFut.foreach{idx =>
				log.info(s"SPARQL magic index restored successfully (${idx.objs.length} objects)")
				IndexHandler.dropStorage()
			}
			indexDataFut.map(Option(_)).recover{
				case err =>
					log.warning(s"Failed to restore SPARQL index (${err.getMessage})")
					None
			}
		}

	val startup = for(
		(citCache, doiCache) <- readCitCache().zip(readDoiCache());
		db <- metaFactory(citCache, doiCache, config);
		metaflow <- Future.fromTry(MetaFlow.initiate(db, config));
		idxOpt <- optIndexDataFut;
		_ = db.initSparqlMagicIndex(idxOpt);
		route = MainRoute(db, metaflow, config);
		//_ = log.info("SPARQL magic index initialized, starting the HTTP server...");
		binding <- Http().newServerAt(config.httpBindInterface, config.port).bind(route)
	) yield {
		sys.addShutdownHook{
			metaflow.cancel()
			try
				Await.result(binding.unbind(), 10.seconds)
			finally
				db.close()
				println("Metadata db has been shut down")

			println("meta service shutdown successful")
		}
		log.info(binding.toString)
	}

	startup.failed.foreach{err =>
		log.error(err, "Could not start meta service")
		system.terminate()
	}
}
