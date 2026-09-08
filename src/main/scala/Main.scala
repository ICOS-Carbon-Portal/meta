package se.lu.nateko.cp.meta

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.Materializer
import io.sentry.Sentry
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.metaflow.MetaFlow
import se.lu.nateko.cp.meta.routes.MainRoute

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

object Main extends App with CpmetaJsonProtocol{

	given system: ActorSystem = ActorSystem("cpmeta", config = AppConfig.rootConfWithWorkingDirOverrides)
	private val log = Logging.getLogger(system, this)
	private given ExecutionContext = system.dispatcher

	val config: CpmetaConfig = ConfigLoader.default
	initSentry(config)
	given EnvriConfigs = config.core.envriConfigs
	val metaFactory = new MetaDbFactory

	val startup = for(
		db <- metaFactory(config);
		metaflow <- Future.fromTry(MetaFlow.initiate(db, config));
		route = MainRoute(db, metaflow, config);
		//_ = log.info("SPARQL magic index initialized, starting the HTTP server...");
		binding <- Http().newServerAt(config.httpBindInterface, config.port).bind(route)
	) yield {
		sys.addShutdownHook {
			metaflow.cancel()
			try {
				Await.result(binding.unbind(), 10.seconds)
			} finally {
				db.close()
				Sentry.close()
				println("Metadata db has been shut down")
			}

			println("meta service shutdown successful")
		}
		log.info(binding.toString)
	}

	startup.failed.foreach{err =>
		Sentry.captureException(err)
		Sentry.flush(5000)
		log.error(err, "Could not start meta service")
		system.terminate()
	}
}

private def initSentry(config: CpmetaConfig): Unit =
	config.sentry match {
		case Some(conf) => Sentry.init(conf.dsn)
		case None => ()
	}
