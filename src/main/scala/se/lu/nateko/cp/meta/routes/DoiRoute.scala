package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.meta.services.upload._
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.StatusCodes
import java.net.URI

object DoiRoute extends CpmetaJsonProtocol{
	def apply(
		service: DoiService,
		authRouting: AuthenticationRouting,
		coreConf: MetaCoreConfig,
		uriSerializer: UriSerializer
	): Route = {

		implicit val configs = coreConf.envriConfigs
		val extractEnvri = AuthenticationRouting.extractEnvriDirective

		path("dois" / Segment){ doiType =>
			post{
				authRouting.mustBeLoggedIn{ _ =>
					extractEnvri{implicit envri =>
						entity(as[URI]){ uri =>
							service.makeDoi(doiType, Uri(uri.toString()), uriSerializer) match {
								case None => complete(StatusCodes.NotFound)
								case Some(doiMeta) =>
									onSuccess(service.saveDoi(doiMeta)){ doi =>
									complete(doi)
								}
							}
						}
					}
				}
			}
		}

	}
}