package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.CpmetaConfig
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import se.lu.nateko.cp.meta.onto.InstOnto
import se.lu.nateko.cp.meta.OntoConfig

object MainRoute {

	val exceptionHandler = ExceptionHandler{
		case ex =>
			val traceWriter = new java.io.StringWriter()
			ex.printStackTrace(new java.io.PrintWriter(traceWriter))
			val trace = traceWriter.toString
			val msg = if(ex.getMessage == null) "" else ex.getMessage
			complete((StatusCodes.InternalServerError, s"$msg\n$trace"))
	}

	def apply(db: MetaDb, config: CpmetaConfig)(implicit mat: Materializer): Route = {

		implicit val sparqlMarsh = db.sparql.marshaller
		val sparqlRoute = SparqlRoute()
		val staticRoute = StaticRoute(config)

		val authRouting = new AuthenticationRouting(config.auth)
		val uploadRoute = UploadApiRoute(db.uploadService, authRouting)

		val metaEntryRouting = new MetadataEntryRouting(authRouting)
		val metaEntryRoute = metaEntryRouting.entryRoute(db.instOntos, config.onto.instOntoServers)

		val labelingRoute = LabelingApiRoute(db.labelingService, authRouting)

		val filesRoute = FilesRoute(db.fileService)

		handleExceptions(exceptionHandler){
			sparqlRoute ~
			metaEntryRoute ~
			staticRoute ~
			uploadRoute ~
			labelingRoute ~
			filesRoute ~
			authRouting.route
		}
	}

}