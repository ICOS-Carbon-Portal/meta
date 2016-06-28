package se.lu.nateko.cp.job

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.Http
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import JobAdJson._
import scala.util.Failure
import scala.util.Success

object Main extends App {
	implicit val system = ActorSystem("cpmeta")
	implicit val materializer = ActorMaterializer(namePrefix = Some("cpmeta_mat"))
	import system.dispatcher
	val log = system.log

	val exceptionHandler = ExceptionHandler{
		case ex =>
			val traceWriter = new java.io.StringWriter()
			ex.printStackTrace(new java.io.PrintWriter(traceWriter))
			val trace = traceWriter.toString
			val msg = if(ex.getMessage == null) "" else ex.getMessage
			complete((StatusCodes.InternalServerError, s"$msg\n$trace"))
	}

	val route = handleExceptions(exceptionHandler){
		get{
			pathEndOrSingleSlash{
				getFromResource("index.html")
			} ~
			path("assignment"){
				complete(AssignmentGenerator.createAssignment)
			}
		} ~
		(post & path("report")){
			entity(as[Report]){report =>
				ReportValidator.validate(report) match{
					case Failure(err) =>
						val msg = err.getMessage
						log.info(s"BAD($msg) ${report.toString}")
						complete((StatusCodes.BadRequest, msg))
					case Success(_) =>
						log.info("GOOD " + report.toString)
						complete((StatusCodes.OK, "Good job!"))
				}
			} ~
			complete((StatusCodes.BadRequest, "Expected a properly formed assignment report JSON as request payload"))
		}
	}

	Http()
		.bindAndHandle(route, "localhost", 9050)
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
