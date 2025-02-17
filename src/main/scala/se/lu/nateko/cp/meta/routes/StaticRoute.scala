package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.{IRI, Literal}
import play.twirl.api.Html
import se.lu.nateko.cp.meta.OntoConfig
import se.lu.nateko.cp.meta.api.{SparqlQuery, SparqlRunner}
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, EnvriConfigs}
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import se.lu.nateko.cp.meta.services.upload.PageContentMarshalling
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import scala.language.postfixOps
import scala.util.Using

object StaticRoute {

	private val pages: PartialFunction[(String, Envri, EnvriConfig), Html] = {
		case ("labeling", envri, envriConfig) => views.html.LabelingPage()(envriConfig, envri)
		case ("sparqlclient", envri, envriConfig) => views.html.SparqlClientPage()(envri, envriConfig)
		case ("station", envri, _) => views.html.StationPage(envri)
	}

	import PageContentMarshalling.twirlHtmlEntityMarshaller

	def apply(sparql: SparqlRunner, config: OntoConfig)(implicit envriConfigs: EnvriConfigs): Route = {

		val extractEnvri = AuthenticationRouting.extractEnvriDirective
		val extractEnvriOpt = AuthenticationRouting.extractEnvriOptDirective

		def uploadGuiRoute(pathPref: String, devVersion: Boolean): Route = (get & pathPrefix(pathPref)){
			extractEnvri{envri =>
				pathSingleSlash {
					val toExclude: Set[URI] = Envri.values.filter(_ != envri)
						.map(e => CitationMaker.defaultLicence(using e).url).toSet

					val licences = getLicences(sparql).filterNot{case (licUri, _) =>
						//exclude other ENVRIES' default licences from the list
						toExclude.contains(licUri)
					}
					complete(views.html.UploadGuiPage(devVersion, licences)(envri, envriConfigs(envri)))
				} ~
				path(Segment){getFromResource}
			}
		}

		(get & pathPrefix("edit" / Segment)){ontId =>
			path("metaentry.js"){
				getFromResource("www/metaentry.js")
			} ~ {
				config.instOntoServers.get(ontId) match {
					case Some(ontConfig) => extractEnvri{envri =>
							pathSingleSlash{
								complete(views.html.MetaentryPage(ontConfig.serviceTitle)(envri, envriConfigs(envri)))
							}
						}
					case None =>
						complete((StatusCodes.NotFound, s"Unrecognized metadata entry project: $ontId"))
				}
			}
		} ~
		uploadGuiRoute("uploadgui", false) ~
		uploadGuiRoute("uploadguidev", true) ~
		path("uploadgui" / "gcmdkeywords.json"){
			getFromResource("gcmdkeywords.json")
		} ~
		(get & pathPrefix(Segment)){page =>
			extractEnvriOpt{envri =>
				val conf = envriConfigs(envri)
				if(pages.isDefinedAt(page, envri, conf)) {
					pathSingleSlash{
						complete(pages(page, envri, conf))
					} ~
					pathEnd{
						redirect(s"/$page/", StatusCodes.Found)
					} ~
					path(s"$page.js"){
						getFromResource(s"www/$page.js")
					}
				} else reject
			}
		} ~
		(post & path("sparqlclient" /)){
			extractEnvri{envri =>
				formField("query"){query =>
					complete(views.html.SparqlClientPage(Some(query))(envri, envriConfigs(envri)))
				} ~
				complete(StatusCodes.BadRequest -> "Expected 'query' form field with SPARQL query content")
			}
		} ~
		path("robots.txt"){
			getFromResource("robots.txt")
		}
	}

	private def getLicences(sparql: SparqlRunner): Seq[(URI, String)] = {
		val q = SparqlQuery(
			"select * where{?licence a <http://purl.org/dc/terms/LicenseDocument>;rdfs:label ?name}"
		)
		Using(sparql.evaluateTupleQuery(q))(
		_.flatMap{bs =>
			List("licence", "name").map(bs.getValue) match{
				case List(lic: IRI, name: Literal) =>
					Some(lic.toJava -> name.stringValue)
				case _ =>
					None
			}
		}.toIndexedSeq.sortBy(_._2)).get
	}
}
