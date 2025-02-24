package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.Statement

final case class RdfUpdate(statement: Statement, isAssertion: Boolean)
