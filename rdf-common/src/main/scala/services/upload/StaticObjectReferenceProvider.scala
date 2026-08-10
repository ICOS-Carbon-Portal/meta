package se.lu.nateko.cp.meta.services.upload

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.api.RdfLens.{DobjConn, DocConn}
import se.lu.nateko.cp.meta.core.data.{CitableItem, References, StaticObject}
import se.lu.nateko.cp.meta.utils.Validated

/**
 * Optional extension point for values derived from a static item's RDF and external DOI data.
 * The reader itself remains usable without this implementation in meta.
 */
trait StaticObjectReferenceProvider:
	def getItemCitationInfo(item: CitableItem): References
	def getCitationInfo(item: StaticObject)(using Envri, DocConn | DobjConn): Validated[References]
