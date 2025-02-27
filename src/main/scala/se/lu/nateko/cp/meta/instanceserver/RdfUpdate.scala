package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.Statement

// TODO: Use Rdf4jStatement instead of Statement.
// Would allow filtering earlier where we use RdfUpdate,
// and get rid of IndexUpdate in se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData
case class RdfUpdate(statement: Statement, isAssertion: Boolean)
