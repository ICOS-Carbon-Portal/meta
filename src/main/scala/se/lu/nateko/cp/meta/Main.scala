package se.lu.nateko.cp.meta

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import se.lu.nateko.cp.meta.routes.MainRoute


object Main extends App with CpmetaJsonProtocol{

	implicit val system = ActorSystem("cpmeta")
	system.log //force log initialization to avoid deadlocks at startup
	implicit val materializer = ActorMaterializer(namePrefix = Some("cpmeta_mat"))
	import system.dispatcher

	val config: CpmetaConfig = ConfigLoader.default

	val startup = for(
		db <- MetaDb(config);
		route = MainRoute(db, config);
		binding <- Http().bindAndHandle(route, "localhost", config.port)
	) yield {
		sys.addShutdownHook{
			val exeCtxt = ExecutionContext.Implicits.global
			val doneFuture = binding
				.unbind()
				.flatMap(_ => system.terminate())(exeCtxt)
			Await.result(doneFuture, 5.seconds)
		}
		system.log.info(binding.toString)
	}

	startup.failed.foreach{err =>
		system.log.error(err, "Could not start meta service")
	}
}
