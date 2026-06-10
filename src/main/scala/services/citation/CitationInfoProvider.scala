package se.lu.nateko.cp.meta.services.citation

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.api.RdfLens.{DobjConn, DocConn, GlobConn}
import se.lu.nateko.cp.meta.core.data.{CitableItem, References, StaticObject}
import se.lu.nateko.cp.meta.utils.Validated

/**
 * Source of the bibliographic [[References]] that get baked into the
 * [[StaticObject]]s and [[se.lu.nateko.cp.meta.core.data.StaticCollection]]s
 * meta serves.
 *
 * There are two implementations:
 *   - the live `CitationMaker` (lives in the citations service), which computes
 *     references from DataCite + the object structure, and
 *   - `MaterializedCitationInfoProvider` (lives in meta), which reads the
 *     references that the citations service has already materialized into the
 *     triplestore.
 *
 * The connection parameters let the materialized implementation read the
 * derived citation graph; the live implementation simply ignores them.
 */
trait CitationInfoProvider:

	def getCitationInfo(sobj: StaticObject)(using Envri, DocConn | DobjConn): Validated[References]

	def getItemCitationInfo(item: CitableItem, itemIri: IRI)(using GlobConn): References

end CitationInfoProvider
