package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement

class AdminRouting(
	sparqler: SparqlRunner,
	servers: Map[String, InstanceServer],
	authRouting: AuthenticationRouting,
	conf: SparqlServerConfig
) {

	private val permitAdmins = authRouting.allowUsers(conf.adminUsers) _

	val route = pathPrefix("admin"){
		permitAdmins{
			pathPrefix("insert")(operationRoute(true)) ~
			pathPrefix("delete")(operationRoute(false))
		} ~
		complete(StatusCodes.Forbidden -> "Only SPARQL admins are allowed here")
	}


	private def operationRoute(insert: Boolean) = path(Segment){server =>
		servers.get(server).fold[Route](
			complete(StatusCodes.NotFound -> s"Instance server $server not found")
		)(
			instServ => entity(as[String]){query =>
				val updates = sparqler.evaluateGraphQuery(SparqlQuery(query)).map(RdfUpdate(_, insert))
				applicationRoute(instServ.writeContextsView, updates)
			}
		)
	}

	private def applicationRoute(server: InstanceServer, updates: Iterator[RdfUpdate]) =
		parameter("dryRun".as[Boolean].?){dryRunOpt =>

			val dryRun = dryRunOpt.fold(true)(identity)

			val updatesStream = updates.filter{upd =>

				val Rdf4jStatement(subj, pred, obj) = upd.statement

				upd.isAssertion ^ server.hasStatement(subj, pred, obj)
			}.to(LazyList)

			if(!dryRun) server.applyAll(updatesStream)

			val responseLines = updatesStream.map{upd =>
				ByteString(s"${if(upd.isAssertion) "+" else "-"} ${upd.statement}\n")
			}

			complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, Source(responseLines)))
		} ~
		complete(StatusCodes.BadRequest -> "URL query parameter 'dryRun' must be a boolean")

}
