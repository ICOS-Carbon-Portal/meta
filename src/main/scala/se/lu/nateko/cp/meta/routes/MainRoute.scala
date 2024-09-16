package se.lu.nateko.cp.meta.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.metaflow.MetaFlow
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.services.upload.DoiService
import se.lu.nateko.cp.meta.services.upload.PageContentMarshalling.errorMarshaller

import scala.concurrent.ExecutionContext

object MainRoute {

	def exceptionHandler(using envriConfigs: EnvriConfigs) = ExceptionHandler{
		case ex =>
			val extractEnvri = AuthenticationRouting.extractEnvriDirective
			extractEnvri { implicit envri =>
				given EnvriConfig = envriConfigs(envri)
				given ToEntityMarshaller[Throwable] = errorMarshaller
				complete(StatusCodes.InternalServerError -> ex)
			}
	}

	def apply(db: MetaDb, metaFlow: MetaFlow, config: CpmetaConfig)(using sys: ActorSystem, ctxt: ExecutionContext): Route =

		given ToResponseMarshaller[SparqlQuery] = db.sparql.marshaller
		given EnvriConfigs = config.core.envriConfigs

		val sparqler = new Rdf4jSparqlRunner(db.magicRepo)
		val sparqlRoute = SparqlRoute(config.sparql)

		val staticRoute = StaticRoute(sparqler, config.onto)
		val authRouting = new AuthenticationRouting(config.auth)
		val authRoute = authRouting.route
		val uploadRoute = UploadApiRoute(db.uploadService, authRouting, metaFlow.uploadServices, config.core)
		val doiService = new DoiService(config.citations.doi, db.uriSerializer)
		val doiRoute = DoiRoute(doiService, authRouting, db.citer.doiCiter, config.core, sys.log)
		val linkedDataRoute = LinkedDataRoute(config.instanceServers, db.uriSerializer, db.instanceServers, db.vocab, sys.log)

		val metaEntryRouting = new MetadataEntryRouting(authRouting)
		val metaEntryRoute = metaEntryRouting.entryRoute(db.instOntos, config.onto.instOntoServers)

		val labelingRoute = LabelingApiRoute(db.labelingService, authRouting, config.sparql.adminUsers)

		val filesRoute = FilesRoute(db.fileService)

		val dtoDlRoute = DtoDownloadRoute(db.uriSerializer)
		val sitemapRoute = SitemapRoute(sparqler)

		val adminRoute = new AdminRouting(db.magicRepo, db.instanceServers, authRouting, db.makeReadonly, config.sparql).route

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
	end apply

}
