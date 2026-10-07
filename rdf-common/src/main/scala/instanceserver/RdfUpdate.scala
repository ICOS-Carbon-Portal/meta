package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.Statement

case class RdfUpdate(statement: Statement, isAssertion: Boolean)

object RdfAssertion {
	def unapply(update: RdfUpdate): Option[Statement] = {
		if (update.isAssertion) {
			Some(update.statement)
		} else {
			None
		}
	}
}

object RdfRetraction {
	def unapply(update: RdfUpdate): Option[Statement] = {
		if (!update.isAssertion) {
			Some(update.statement)
		} else {
			None
		}
	}
}
