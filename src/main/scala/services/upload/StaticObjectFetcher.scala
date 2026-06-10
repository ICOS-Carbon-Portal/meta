package se.lu.nateko.cp.meta.services.upload

import akka.http.scaladsl.model.Uri
import se.lu.nateko.cp.meta.core.data.{StaticCollection, StaticObject}
import se.lu.nateko.cp.meta.utils.Validated

/**
 * Fetches the fully-assembled (citation-enriched) static objects and
 * collections behind landing-page URIs.
 *
 * Two implementations exist:
 *   - `Rdf4jUriSerializer`, which reads from meta's own triplestore (citation
 *     info comes from the materialized graph and may lag behind), and
 *   - `RemoteCitationFetcher`, which asks the citations service to compute a
 *     fresh object on the fly — used by the DOI-minting path, which must not
 *     work off stale citation metadata.
 */
trait StaticObjectFetcher:
	def fetchStaticObject(uri: Uri): Validated[StaticObject]
	def fetchStaticCollection(uri: Uri): Validated[StaticCollection]
