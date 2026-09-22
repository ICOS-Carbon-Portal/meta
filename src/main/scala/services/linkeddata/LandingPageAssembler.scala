package se.lu.nateko.cp.meta.services.linkeddata

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.api.{OrganizationExtra, PersonExtra, StatisticsClient}
import se.lu.nateko.cp.meta.core.data.{Instrument, Organization, StaticCollection, StaticObject, Station}
import se.lu.nateko.cp.meta.views.ResourceViewInfo

import scala.concurrent.Future

/**
 * Adds request-scoped, non-RDF information to metadata read through the meta connection.
 *
 * The caller supplies the metadata request. This class deliberately performs its statistics
 * requests only after that request has completed, so it never owns or extends an RDF connection
 * lifetime.
 */
final class LandingPageAssembler(statistics: StatisticsClient):
	def staticObject(metadata: StaticObject, warnings: Seq[String])(using Envri): Future[LandingPage.StaticObjectPage] =
		import statistics.executionContext
		// Concurrently fetch statistics
		val downloadsResult = statistics.getObjDownloadCount(metadata)
		val previewsResult = statistics.getPreviewCount(metadata.hash)
		for
			downloads <- downloadsResult
			previews <- previewsResult
		yield LandingPage.StaticObjectPage(
			metadata,
			LandingPageMetrics(downloads, previews),
			warnings
		)

	def staticCollection(metadata: StaticCollection, warnings: Seq[String])(using Envri): Future[LandingPage.StaticCollectionPage] =
		import statistics.executionContext
		statistics.getCollDownloadCount(metadata.res).map: downloads =>
			LandingPage.StaticCollectionPage(
				metadata,
				LandingPageMetrics(downloads, None),
				warnings
			)

	def station(metadata: OrganizationExtra[Station], warnings: Seq[String]): LandingPage.StationPage =
		LandingPage.StationPage(metadata, warnings)

	def organization(metadata: OrganizationExtra[Organization], warnings: Seq[String]): LandingPage.OrganizationPage =
		LandingPage.OrganizationPage(metadata, warnings)

	def instrument(metadata: Instrument, warnings: Seq[String]): LandingPage.InstrumentPage =
		LandingPage.InstrumentPage(metadata, warnings)

	def person(metadata: PersonExtra, warnings: Seq[String]): LandingPage.PersonPage =
		LandingPage.PersonPage(metadata, warnings)

	def genericResource(metadata: ResourceViewInfo, warnings: Seq[String] = Nil): LandingPage.GenericResourcePage =
		LandingPage.GenericResourcePage(metadata, warnings)
