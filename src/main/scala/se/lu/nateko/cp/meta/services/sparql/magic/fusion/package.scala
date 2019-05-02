package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.query.algebra.StatementPattern

package object fusion{
	def splitTriple(sp: StatementPattern) = (sp.getSubjectVar, sp.getPredicateVar, sp.getObjectVar)
}
