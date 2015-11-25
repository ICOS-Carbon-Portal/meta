package se.lu.nateko.cp.meta

import akka.actor.ActorSystem
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import se.lu.nateko.cp.meta.routes.MainRoute


object Main extends App with CpmetaJsonProtocol{

	val logger = org.slf4j.LoggerFactory.getLogger("Cpmeta")

	implicit val system = ActorSystem("cpmeta")
	implicit val materializer = ActorMaterializer(namePrefix = Some("cpmeta_mat"))

	implicit val dispatcher = system.dispatcher
	implicit val scheduler = system.scheduler

	val config: CpmetaConfig = ConfigLoader.default
	val db: MetaDb = MetaDb(config)

	val route = MainRoute(db, config)

	Http()
		.bindAndHandle(route, "localhost", config.port)
		.onSuccess{
			case binding =>
				sys.addShutdownHook{
					val doneFuture = binding.unbind().andThen{
						case _ =>
							db.close()
							system.shutdown()
							logger.info("Shutdown completed")
					}
					Await.result(doneFuture, 3 seconds)
				}
				val port = binding.localAddress.getPort
				logger.info(s"Server started, listening on port $port")
		}

}
