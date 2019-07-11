package se.lu.nateko.cp.meta.routes

import scala.language.postfixOps
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.meta.ConfigLoader.dObjGraphInfoFormat
import se.lu.nateko.cp.meta.InstanceServersConfig
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.routes.FilesRoute.Sha256Segment
import se.lu.nateko.cp.meta.services.linkeddata.InstanceServerSerializer
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import spray.json.DefaultJsonProtocol._

object LinkedDataRoute {
	private implicit val instServerMarshaller = InstanceServerSerializer.marshaller

	def apply(
		config: InstanceServersConfig,
		uriSerializer: UriSerializer,
		instanceServers: Map[String, InstanceServer]
	)(implicit envriConfs: EnvriConfigs): Route = {

		val instServerConfs = MetaDb.getAllInstanceServerConfigs(config)
		implicit val uriMarshaller = uriSerializer.marshaller
		val extractEnvri = AuthenticationRouting.extractEnvriDirective

		val genericRdfUriResourcePage: Route = (extractUri & extractEnvri){(uri, envri) =>
			extractHost{hostname =>
				val scheme = if(envri == Envri.ICOS){
					import Uri.Path.{Segment, Slash}

					uri.path match{
						case Slash(Segment("objects", _)) => "https" //objects have HTTPS URIs in our RDF
						case _ => "http"
					}
				} else "https"

				complete(uri.withHost(hostname).withScheme(scheme))
			}
		}

		get{
			path(("ontologies" | "resources") / Segment /){_ =>
				extractUri{uri =>
					val path = uri.path.toString

					val serverOpt: Option[(String, InstanceServer)] = instServerConfs.collectFirst{
						case (id, instServConf)
							if instServConf.writeContexts.exists(_.toString.endsWith(path)) =>
								instanceServers.get(id).map((id, _))
					}.flatten

					serverOpt match{
						case None =>
							complete(StatusCodes.NotFound)
						case Some((id, instServer)) =>
							respondWithHeader(attachmentHeader(id + ".rdf")){
								complete(instServer)
							}
					}
				}
			} ~
			pathPrefix("objects" / Sha256Segment){_ =>
				pathEnd{
					respondWithHeaders(`Access-Control-Allow-Origin`.*) {
						genericRdfUriResourcePage
					}
				} ~
				path(Segment){
					case fileName @ FileNameWithExtension(_, ext) =>
						extToMime.get(ext).fold[Route](reject){mime =>
							mapRequest(rewriteObjRequest(mime)){
								respondWithHeader(attachmentHeader(fileName)){
									genericRdfUriResourcePage
								}
							}
						}
					case _ =>
						reject
				}
			} ~
			pathPrefix("ontologies" | "resources" | "files" | "collections"){
				genericRdfUriResourcePage
			} ~
			path("config" / "dataObjectGraphInfos"){
				extractEnvri{implicit envri =>
					respondWithHeader(`Access-Control-Allow-Origin`.*){
						complete(MetaDb.getDobjGraphInfos(config))
					}
				}
			}
		} ~
		options{
			pathPrefix("objects" / Sha256Segment) { _ =>
				respondWithHeaders(
					`Access-Control-Allow-Origin`.*,
					`Access-Control-Allow-Methods`(HttpMethods.GET),
					`Access-Control-Allow-Headers`(`Content-Type`.name, `Cache-Control`.name)
				) {
					complete(StatusCodes.OK)
				}
			}
		}
	}

	private val FileNameWithExtension = "^(.+)(\\.[a-z]+)$".r

	private val extToMime: Map[String, MediaType] = Map(
		".json" -> MediaTypes.`application/json`,
		".ttl" -> MediaTypes.`text/plain`,
		".xml" -> MediaTypes.`application/xml`
	)

	private def rewriteObjRequest(mime: MediaType)(req: HttpRequest): HttpRequest = {
		val newPath = req.uri.path.reverse.tail.tail.reverse
		val newUri = req.uri.withPath(newPath)
		val accept = Accept(mime)
		req.removeHeader(accept.name).addHeader(accept).withUri(newUri)
	}

	def attachmentHeader(fileName: String) = {
		import ContentDispositionTypes.attachment
		`Content-Disposition`(attachment, Map("filename" -> fileName))
	}
}
