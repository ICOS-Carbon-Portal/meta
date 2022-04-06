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
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import se.lu.nateko.cp.meta.services.upload.PageContentMarshalling.errorMarshaller
import se.lu.nateko.cp.meta.icos.MetaFlow
import se.lu.nateko.cp.meta.services.upload.DoiService
import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem

object MainRoute {

	def exceptionHandler(implicit envriConfigs: EnvriConfigs) = ExceptionHandler{
		case ex =>
			val extractEnvri = AuthenticationRouting.extractEnvriDirective
			extractEnvri { implicit envri =>
				implicit val envriConfig = envriConfigs(envri)
				implicit val errMarsh = errorMarshaller
				complete(StatusCodes.InternalServerError -> ex)
			}
	}

	def apply(db: MetaDb, metaFlow: MetaFlow, config: CpmetaConfig)(implicit system: ActorSystem, ctxt: ExecutionContext): Route = {

		implicit val sparqlMarsh = db.sparql.marshaller
		implicit val envriConfigs = config.core.envriConfigs

		val sparqler = new Rdf4jSparqlRunner(db.repo)
		val sparqlRoute = SparqlRoute(config.sparql)

		val staticRoute = StaticRoute(sparqler, config.onto)
		val authRouting = new AuthenticationRouting(config.auth)
		val authRoute = authRouting.route
		val uploadRoute = UploadApiRoute(db.uploadService, authRouting, metaFlow.atcSource, config.core)
		val doiService = new DoiService(config, db.uriSerializer)
		val doiRoute = DoiRoute(doiService, authRouting, config.core)
		val linkedDataRoute = LinkedDataRoute(config.instanceServers, db.uriSerializer, db.instanceServers)

		val metaEntryRouting = new MetadataEntryRouting(authRouting)
		val metaEntryRoute = metaEntryRouting.entryRoute(db.instOntos, config.onto.instOntoServers)

		val labelingRoute = LabelingApiRoute(db.labelingService, authRouting)

		val filesRoute = FilesRoute(db.fileService)

		val dtoDlRoute = DtoDownloadRoute(db.uriSerializer)
		val sitemapRoute = SitemapRoute(sparqler)

		val adminRoute = new AdminRouting(sparqler, db.instanceServers, authRouting, config.sparql).route

		handleExceptions(exceptionHandler){
			sparqlRoute ~
			metaEntryRoute ~
			uploadRoute ~
			doiRoute ~
			labelingRoute ~
			filesRoute ~
			authRoute ~
			staticRoute ~
			linkedDataRoute ~
			sitemapRoute ~
			adminRoute ~
			dtoDlRoute ~
			path("buildInfo"){
				complete(se.lu.nateko.cp.meta.BuildInfo.toString)
			}
		}
	}

}
