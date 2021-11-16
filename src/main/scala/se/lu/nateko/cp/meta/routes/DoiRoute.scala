package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.meta.services.upload._
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import java.net.URI
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.Envri

object DoiRoute extends CpmetaJsonProtocol{
	def apply(
		service: DoiService,
		authRouting: AuthenticationRouting,
		coreConf: MetaCoreConfig
	): Route = {

		implicit val configs = coreConf.envriConfigs
		val extractEnvri = AuthenticationRouting.extractEnvriDirective

		pathPrefix("create-draft-doi"){
			post{
				authRouting.mustBeLoggedIn{ _ =>
					extractEnvri{implicit envri =>
						entity(as[URI]){ uri =>
							pathPrefix("data"){
								onSuccess(service.makeDataObjectDoi(uri)){ doiMeta =>
									onSuccess(service.saveDoi(doiMeta)){ doi =>
										complete(doi)
									}
								}
							} ~
							pathPrefix("document"){
								onSuccess(service.makeDocObjectDoi(uri)){ doiMeta =>
									onSuccess(service.saveDoi(doiMeta)){ doi =>
										complete(doi)
									}
								}
							} ~
							pathPrefix("collection"){
								onSuccess(service.makeCollectionDoi(uri)){ doiMeta =>
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
}