package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.CpmetaConfig
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.Route
import akka.stream.Materializer

object MainRoute {

	def apply(db: MetaDb, config: CpmetaConfig)(implicit mat: Materializer): Route = {

		val exceptionHandler = ExceptionHandler{
			case ex =>
				val traceWriter = new java.io.StringWriter()
				ex.printStackTrace(new java.io.PrintWriter(traceWriter))
				val trace = traceWriter.toString
				val msg = if(ex.getMessage == null) "" else ex.getMessage
				complete((StatusCodes.InternalServerError, s"$msg\n$trace"))
		}

		val sparqlRoute = SparqlRoute(db.sparql)
		val staticRoute = StaticRoute(config)
		val metaEntryApiRoute = MetadataEntryApiRoute(db.instOnto)

		handleExceptions(exceptionHandler){
			metaEntryApiRoute ~
			staticRoute ~
			sparqlRoute
		}
	}

}