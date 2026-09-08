package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Origin`
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.api.StatisticsClient
import se.lu.nateko.cp.meta.core.data.{EnvriConfigs, collectionPathPrefix, objectPathPrefix}
import se.lu.nateko.cp.meta.routes.FilesRoute.Sha256Segment
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.ExecutionContext

/**
 * Landing page statistics, served separately from the landing pages themselves, so that
 * a slow (or unavailable) statistics service cannot hold up the rendering of the pages.
 */
object StatisticsRoute extends DefaultJsonProtocol:

	case class ObjectStatistics(downloads: Option[Int], previews: Option[Int])
	case class CollectionStatistics(downloads: Option[Int])

	given RootJsonFormat[ObjectStatistics] = jsonFormat2(ObjectStatistics.apply)
	given RootJsonFormat[CollectionStatistics] = jsonFormat1(CollectionStatistics.apply)

	def apply(stats: StatisticsClient)(using EnvriConfigs, ExecutionContext): Route =
		val extractEnvri = AuthenticationRouting.extractEnvriDirective
		val objects = objectPathPrefix.stripSuffix("/")
		val collections = collectionPathPrefix.stripSuffix("/")

		(get & extractEnvri){implicit envri: Envri =>
			respondWithHeaders(`Access-Control-Allow-Origin`.*){
				path(objects / Sha256Segment / "statistics"){hash =>
					val dlCountFut = stats.getObjDownloadCount(hash)
					val previewCountFut = stats.getPreviewCount(hash)
					complete(
						for
							dlCount <- dlCountFut
							previewCount <- previewCountFut
						yield ObjectStatistics(dlCount, previewCount)
					)
				} ~
				path(collections / Sha256Segment / "statistics"){hash =>
					complete(stats.getCollDownloadCount(hash).map(CollectionStatistics.apply))
				}
			}
		}

end StatisticsRoute
