package se.lu.nateko.cp.meta.instanceserver

import org.openrdf.model.Statement

case class RdfUpdate(statement: Statement, isAssertion: Boolean)
