package se.lu.nateko.cp.meta.routes

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
		coreConf: MetaCoreConfig,
		uriSerializer: UriSerializer
	): Route = {

		def saveDoi(doiMeta: Option[DoiMeta])(implicit envri: Envri): Route = doiMeta match {
			case None => complete(StatusCodes.NotFound)
			case Some(doiMeta) =>
				onSuccess(service.saveDoi(doiMeta)){ doi =>
					complete(doi)
				}
		}

		implicit val configs = coreConf.envriConfigs
		val extractEnvri = AuthenticationRouting.extractEnvriDirective

		pathPrefix("dois"){
			post{
				authRouting.mustBeLoggedIn{ _ =>
					extractEnvri{implicit envri =>
						entity(as[URI]){ uri =>
							path("data"){
								saveDoi(uriSerializer.fetchStaticObject(Uri(uri.toString)).flatMap(_.asDataObject).map(service.makeDataObjectDoi(_, uri)))
							} ~
							path("document"){
								saveDoi(uriSerializer.fetchStaticObject(Uri(uri.toString)).map(service.makeDocObjectDoi(_, uri)))
							} ~
							path("collection"){
								saveDoi(uriSerializer.fetchStaticCollection(Uri(uri.toString)).map(service.makeCollectionDoi(_, uri)))
							}
						}
					}
				}
			}
		}

	}
}