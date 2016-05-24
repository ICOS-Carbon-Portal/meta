package se.lu.nateko.cp.meta

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import se.lu.nateko.cp.meta.routes.MainRoute


object Main extends App with CpmetaJsonProtocol{
	//needed to initialize logging, even if not used in this class
	val logger = org.slf4j.LoggerFactory.getLogger("Cpmeta")

	implicit val system = ActorSystem("cpmeta")
	implicit val materializer = ActorMaterializer(namePrefix = Some("cpmeta_mat"))
	import system.dispatcher

	val config: CpmetaConfig = ConfigLoader.default
	val db: MetaDb = MetaDb(config)

	val route = MainRoute(db, config)

	Http()
		.bindAndHandle(route, "localhost", config.port)
		.onSuccess{
			case binding =>
				sys.addShutdownHook{
					val exeCtxt = ExecutionContext.Implicits.global
					val doneFuture = binding
						.unbind()
						.flatMap(_ => system.terminate())(exeCtxt)
					Await.result(doneFuture, 3 seconds)
				}
				println(binding)
		}

}
