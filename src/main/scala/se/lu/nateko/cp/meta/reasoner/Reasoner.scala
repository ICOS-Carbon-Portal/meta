package se.lu.nateko.cp.meta.reasoner

import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLProperty
import org.semanticweb.owlapi.model.OWLClassExpression

trait Reasoner extends java.io.Closeable{
	def getSuperClasses(owlClass: OWLClass, direct: Boolean): Seq[OWLClassExpression]
	def isFunctional(prop: OWLProperty): Boolean
	def getPropertiesWhoseDomainIncludes(owlClass: OWLClass): Seq[OWLProperty]
	def getTopLevelClasses: Seq[OWLClass]
	def isSubClass(subClass: OWLClassExpression, superClass: OWLClassExpression): Boolean
}
