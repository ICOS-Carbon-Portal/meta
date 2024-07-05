package se.lu.nateko.cp.meta.routes

import akka.Done
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
import se.lu.nateko.cp.meta.utils.rdf4j.transact

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success


class AdminRouting(
	repo: Repository,
	servers: Map[String, InstanceServer],
	authRouting: AuthenticationRouting,
	makeMetaReadonly: String => Future[String],
	conf: SparqlServerConfig
) {
	import AuthenticationRouting.optEnsureLocalRequest
	private val permitAdmins = authRouting.allowUsers(conf.adminUsers) _
	private val sparqler = new Rdf4jSparqlRunner(repo)

	private val readonlyModeRoute = (post & withoutRequestTimeout){
		val msg = "Metadata service is in read-only maintenance mode. Please try the write operation again later."
		onComplete(makeMetaReadonly(msg)){
			case Success(successMsg) => complete(StatusCodes.OK -> successMsg)
			case Failure(err) => complete(StatusCodes.InternalServerError -> err.getMessage)
		}
	}

	val route = pathPrefix("admin"){
		path("switchToReadonlyMode"){
			optEnsureLocalRequest{readonlyModeRoute} ~
			permitAdmins{readonlyModeRoute}
		} ~
		permitAdmins{
			pathPrefix("insert")(operationRoute(true)) ~
			pathPrefix("delete")(operationRoute(false)) ~
			//low level, only to be used when the 'delete' functionality is not working:
			path("dropRdf4jTripleObjects")(dropTripleObjectsRoute) ~
			complete(StatusCodes.BadRequest -> "Unrecognized or invalid admin request")
		} ~
		complete(StatusCodes.Forbidden -> "Only SPARQL admins are allowed here")
	}

	private val dropTripleObjectsRoute: Route = parameters("subject", "predicate", "context".?){(subjS, predS, ctxtOS) =>
		val f = repo.getValueFactory
		val subj = f.createIRI(subjS)
		val pred = f.createIRI(predS)
		val ctxts = ctxtOS.map(f.createIRI).toSeq
		repo.transact(_.remove(subj, pred, null, ctxts*)) match
			case Success(_) => complete(StatusCodes.OK)
			case Failure(exc) => complete(StatusCodes.InternalServerError -> exc.getMessage)
	}

	private def operationRoute(insert: Boolean) = path(Segment){server =>
		servers.get(server).fold[Route](
			complete(StatusCodes.NotFound -> s"Instance server $server not found")
		)(
			instServ => entity(as[String]){query =>
				val updates = sparqler.evaluateGraphQuery(SparqlQuery(query))
				applicationRoute(instServ.writeContextsView, updates, insert)
			}
		)
	}

	private def applicationRoute(server: InstanceServer, updates: Iterator[Statement], insert: Boolean) =
		parameter("dryRun".as[Boolean].?){dryRunOpt =>

			val dryRun = dryRunOpt.getOrElse(true)

			val updatesList = server.access: conn ?=>
				updates.collect:
					//OBS Blank-node-subject statements will be dropped here
					case s @ Rdf4jStatement(subj, pred, obj) if insert ^ conn.hasStatement(subj, pred, obj) => RdfUpdate(s, insert)
				.toIndexedSeq

			if(!dryRun) server.applyAll(updatesList)()

			val responseLines = updatesList.map{upd =>
				ByteString(s"${if(upd.isAssertion) "+" else "-"} ${upd.statement}\n")
			}

			complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, Source(responseLines)))
		} ~
		complete(StatusCodes.BadRequest -> "URL query parameter 'dryRun' must be a boolean")

}
