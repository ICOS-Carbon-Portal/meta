package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.CpmetaConfig
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.core.data.Envri

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
		implicit val envriConfigs = config.core.envriConfigs

		val sparqlRoute = SparqlRoute()
		val staticRoute = StaticRoute(config.onto, config.auth(Envri.ICOS))
		val authRouting = new AuthenticationRouting(config.auth)
		val authRoute = authRouting.route
		val uploadRoute = UploadApiRoute(db.uploadService, authRouting, config.core)
		val linkedDataRoute = LinkedDataRoute(config.instanceServers, db.uriSerializer, db.instanceServers)

		val metaEntryRouting = new MetadataEntryRouting(authRouting)
		val metaEntryRoute = metaEntryRouting.entryRoute(db.instOntos, config.onto.instOntoServers)

		val labelingRoute = LabelingApiRoute(db.labelingService, authRouting)

		val filesRoute = FilesRoute(db.fileService)

		val adminRoute = {
			val sparqler = new Rdf4jSparqlRunner(db.repo)
			new AdminRouting(sparqler, db.instanceServers, authRouting, config.sparql).route
		}

		handleExceptions(exceptionHandler){
			sparqlRoute ~
			metaEntryRoute ~
			uploadRoute ~
			labelingRoute ~
			filesRoute ~
			authRoute ~
			staticRoute ~
			linkedDataRoute ~
			adminRoute ~
			path("buildInfo"){
				complete(se.lu.nateko.cp.meta.BuildInfo.toString)
			}
		}
	}

}
