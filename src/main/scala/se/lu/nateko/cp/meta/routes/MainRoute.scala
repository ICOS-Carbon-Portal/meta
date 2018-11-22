package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.CpmetaConfig
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.api.Doi
import se.lu.nateko.cp.meta.api.CitationClient
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

	def apply(db: MetaDb, config: CpmetaConfig)(implicit mat: Materializer, system: ActorSystem): Route = {

		implicit val sparqlMarsh = db.sparql.marshaller
		implicit val _ = config.core.envriConfigs
		val sparqlRoute = SparqlRoute()
		val staticRoute = StaticRoute(config.onto, config.auth(Envri.ICOS))
		val linkedDataRoute = LinkedDataRoute(config.instanceServers, db.uriSerializer, db.instanceServers)

		val authRouting = new AuthenticationRouting(config.auth)
		val authRoute = authRouting.route
		val citer = new CitationClient(getDois(db), config.citations)
		val uploadRoute = UploadApiRoute(db.uploadService, authRouting, citer, config.core)

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

	private def getDois(db: MetaDb): List[Doi] = {
		import se.lu.nateko.cp.meta.services.CpmetaVocab
		import se.lu.nateko.cp.meta.utils.rdf4j._
		val meta = new CpmetaVocab(db.repo.getValueFactory)
		db.repo
			.access{conn =>
				conn.getStatements(null, meta.hasDoi, null)
			}
			.map(_.getObject.stringValue)
			.toList.distinct.collect{
				case Doi(doi) => doi
			}
	}

}
