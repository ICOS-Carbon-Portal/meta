package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.Statement

/**
 * Pattern-matches the assertion half of an `RdfUpdate`. Lives here rather than next to
 * `RdfUpdate` in rdf-common because `metaflow/RdfDiffCalc.scala` is its only user: computing a
 * diff is shared, but reacting to individual assertions is meta's write-side concern. (The
 * mirror-image `RdfRetraction` extractor had no users at all and was deleted.)
 */
object RdfAssertion:
	def unapply(update: RdfUpdate): Option[Statement] =
		if update.isAssertion then Some(update.statement) else None
