package se.lu.nateko.cp.meta.routes

import scala.language.implicitConversions

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.meta.services.upload._
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.StatusCodes
import java.net.URI
import se.lu.nateko.cp.doi.DoiMeta

object DoiRoute extends CpmetaJsonProtocol{
	def apply(
		service: DoiService,
		authRouting: AuthenticationRouting,
		coreConf: MetaCoreConfig
	): Route = {

		implicit val configs = coreConf.envriConfigs
		val extractEnvri = AuthenticationRouting.extractEnvriDirective

		pathPrefix("dois" / "createDraft"){
			post{
				authRouting.mustBeLoggedIn{ _ =>
					extractEnvri{implicit envri =>
						entity(as[URI]){ uri =>
							onSuccess(service.createDraftDoi(uri)){doiOpt =>
								doiOpt.fold(
									complete(StatusCodes.NotFound -> s"Resource with landing page $uri not found, no DOI created")
								)(complete(_))
							}
						} ~
						complete(StatusCodes.BadRequest -> "Expected JSON string with landing page URL as payload")
					}
				}
			} ~
			complete(StatusCodes.BadRequest -> "Only POST requests to this URL")
		}

	}
}