package se.lu.nateko.cp.meta

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.Materializer
import se.lu.nateko.cp.meta.icos.MetaFlow
import se.lu.nateko.cp.meta.routes.MainRoute
import se.lu.nateko.cp.meta.services.sparql.magic.IndexHandler

object Main extends App with CpmetaJsonProtocol{

	given system: ActorSystem = ActorSystem("cpmeta", config = ConfigLoader.appConfig)
	system.log //force log initialization to avoid deadlocks at startup
	import system.dispatcher

	val config: CpmetaConfig = ConfigLoader.default
	val metaFactory = new MetaDbFactory
	system.log.info("Trying to restore SPARQL magic index...")
	val indexDataFut = IndexHandler.restore()
	val startup = for(
		idxOpt <- indexDataFut.map(Option(_)).recover{
			case err =>
				system.log.info(s"Failed to restore SPARQL index (${err.getMessage})")
				None
		};
		db <- metaFactory(config, idxOpt);
		metaflow <- Future.fromTry(MetaFlow.initiate(db, config));
		route = MainRoute(db, metaflow, config);
		binding <- Http().newServerAt("localhost", config.port).bind(route)
	) yield {
		sys.addShutdownHook{
			metaflow.cancel()
			db.close()
			println("Metadata db has been shut down")
			val doneFuture = binding.unbind()
				.flatMap(_ => system.terminate())(ExecutionContext.Implicits.global)
			Await.result(doneFuture, 5.seconds)
			println("meta service shutdown successful")
		}
		system.log.info(binding.toString)
	}

	startup.failed.foreach{err =>
		system.log.error(err, "Could not start meta service")
		system.terminate()
	}
}
