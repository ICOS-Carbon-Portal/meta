package se.lu.nateko.cp.meta.services.linkeddata

import eu.icoscp.envri.Envri
import play.twirl.api.Html
import se.lu.nateko.cp.meta.api.{OrganizationExtra, PersonExtra}
import se.lu.nateko.cp.meta.core.HandleProxiesConfig
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, Instrument, Organization, StaticCollection, StaticObject, Station}
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.views.{LandingPageExtras, ResourceViewInfo}

/**
 * The fully assembled, I/O-free input to a landing-page renderer.
 *
 * Metadata readers and external clients may be used while constructing this value, but a
 * renderer must only need the value itself and the request configuration. This keeps template
 * rendering deterministic and prevents it from triggering more metadata requests.
 */
sealed trait LandingPage:
	def metrics: LandingPageMetrics
	def warnings: Seq[String]

object LandingPage:
	final case class StaticObjectPage(
		objectMetadata: StaticObject,
		metrics: LandingPageMetrics,
		warnings: Seq[String]
	) extends LandingPage

	final case class StaticCollectionPage(
		collectionMetadata: StaticCollection,
		metrics: LandingPageMetrics,
		warnings: Seq[String]
	) extends LandingPage

	final case class StationPage(
		station: OrganizationExtra[Station],
		warnings: Seq[String]
	) extends LandingPage:
		val metrics = LandingPageMetrics.empty

	final case class OrganizationPage(
		organization: OrganizationExtra[Organization],
		warnings: Seq[String]
	) extends LandingPage:
		val metrics = LandingPageMetrics.empty

	final case class InstrumentPage(
		instrument: Instrument,
		warnings: Seq[String]
	) extends LandingPage:
		val metrics = LandingPageMetrics.empty

	final case class PersonPage(
		person: PersonExtra,
		warnings: Seq[String]
	) extends LandingPage:
		val metrics = LandingPageMetrics.empty

	final case class GenericResourcePage(
		resource: ResourceViewInfo,
		warnings: Seq[String] = Nil
	) extends LandingPage:
		val metrics = LandingPageMetrics.empty

/** Values collected for a page which are not part of its RDF metadata. */
final case class LandingPageMetrics(
	downloadCount: Option[Int],
	previewCount: Option[Int]
)

object LandingPageMetrics:
	val empty: LandingPageMetrics = LandingPageMetrics(None, None)

/** Renders a previously assembled page. It deliberately performs no I/O. */
final class LandingPageRenderer(handleProxies: HandleProxiesConfig, vocab: CpVocab):
	def render(page: LandingPage)(using Envri, EnvriConfig): Html =
		val extras = LandingPageExtras(
			page.metrics.downloadCount,
			page.metrics.previewCount,
			page.warnings
		)
		page match
			case LandingPage.StaticObjectPage(metadata, _, _) =>
				given CpVocab = vocab
				views.html.LandingPage(metadata, extras, handleProxies)
			case LandingPage.StaticCollectionPage(metadata, _, _) =>
				views.html.CollectionLandingPage(metadata, extras, handleProxies)
			case LandingPage.StationPage(station, warnings) =>
				views.html.StationLandingPage(station, vocab, warnings)
			case LandingPage.OrganizationPage(organization, warnings) =>
				views.html.OrgLandingPage(organization, warnings)
			case LandingPage.InstrumentPage(instrument, warnings) =>
				views.html.InstrumentLandingPage(instrument, warnings)
			case LandingPage.PersonPage(person, warnings) =>
				views.html.PersonLandingPage(person, warnings)
			case LandingPage.GenericResourcePage(resource, _) =>
				views.html.UriResourcePage(resource)
