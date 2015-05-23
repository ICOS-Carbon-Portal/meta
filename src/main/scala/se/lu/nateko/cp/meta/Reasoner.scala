package se.lu.nateko.cp.meta

import scala.collection.JavaConversions._
import Utils._
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLProperty
import org.semanticweb.owlapi.model.OWLClassExpression
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.parameters.Imports
import org.semanticweb.owlapi.model.OWLDataProperty
import org.semanticweb.owlapi.model.OWLObjectProperty
import org.semanticweb.owlapi.search.EntitySearcher

trait Reasoner extends java.io.Closeable{
	def getPropertiesWhoseDomainIncludes(owlClass: OWLClass): Seq[OWLProperty]
	def getTopLevelClasses: Seq[OWLClass]
	def isSubClass(subClass: OWLClassExpression, superClass: OWLClassExpression): Boolean
}

class HermitBasedReasoner(ontology: OWLOntology) extends Reasoner{
	
	val reasoner = new org.semanticweb.HermiT.Reasoner.ReasonerFactory()
		.createReasoner(ontology)
	private val factory = ontology.getOWLOntologyManager.getOWLDataFactory

	override def close(): Unit = {
		reasoner.dispose()
	}

	def isSubClass(subClass: OWLClassExpression, superClass: OWLClassExpression): Boolean = {
		val axiom = factory.getOWLSubClassOfAxiom(subClass, superClass)
		reasoner.isEntailed(axiom)
	}

	def getPropertiesWhoseDomainIncludes(owlClass: OWLClass): Seq[OWLProperty] = {
		val dataProps = ontology.getDataPropertiesInSignature(Imports.EXCLUDED)
		val objProps = ontology.getObjectPropertiesInSignature(Imports.EXCLUDED)
		(dataProps.toSeq ++ objProps).collect{
			case p: OWLProperty if(isSubClass(owlClass, getFullDomain(p))) => p
		}
	}

	def getTopLevelClasses: Seq[OWLClass] = reasoner
		.getSubClasses(factory.getOWLThing, true)
		.getFlattened
		.toSeq

	def getOwnDomains(owlProp: OWLProperty): Seq[OWLClassExpression] = owlProp match {
		case dp: OWLDataProperty => EntitySearcher.getDomains(dp, ontology).toSeq
		case op: OWLObjectProperty => EntitySearcher.getDomains(op, ontology).toSeq
		case _ => Nil //ignoring annotation properties
	}
	
	def getParentProps(owlProp: OWLProperty): Seq[OWLProperty] = owlProp match {
		case dp: OWLDataProperty => reasoner.getSuperDataProperties(dp, false).getFlattened.toSeq
		case op: OWLObjectProperty =>
			reasoner.getSuperObjectProperties(op, false).getFlattened.toSeq.collect{
				case op: OWLObjectProperty => op
			}
		case _ => Nil //ignoring annotation properties
	}
	
	def getFullDomain(owlProp: OWLProperty): OWLClassExpression = {
		val domains: Seq[OWLClassExpression] =
			(getParentProps(owlProp) :+ owlProp).flatMap(getOwnDomains)
		domains.size match{
			case 0 => factory.getOWLNothing
			case 1 => domains.head
			case _ => factory.getOWLObjectIntersectionOf(domains.toSet)
		}
	}
}