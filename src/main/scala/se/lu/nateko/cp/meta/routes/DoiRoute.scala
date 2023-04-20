package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.doi.DoiMeta
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.citation.CitationClient
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import se.lu.nateko.cp.meta.services.upload.*

import java.net.URI
import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Success

object DoiRoute extends CpmetaJsonProtocol{
	def apply(
		service: DoiService,
		authRouting: AuthenticationRouting,
		doiCitClient: CitationClient,
		coreConf: MetaCoreConfig
	): Route = {

		given EnvriConfigs = coreConf.envriConfigs
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
						requirePost
					}
				}
			} ~
			complete(StatusCodes.BadRequest -> "Only POST requests to this URL")
		} ~
		pathPrefix("dois" / "dropCache"){
			post{
				path(Remaining){maybeDoi =>
					doiCitClient.dropCache(Doi.parse(maybeDoi).get)
					complete(StatusCodes.OK)
				}
			} ~
			requirePost
		}

	}

	private def requirePost = complete(StatusCodes.BadRequest -> "Expected JSON string with landing page URL as payload")
}