package se.lu.nateko.cp.meta.reasoner

import scala.collection.JavaConversions._
import org.semanticweb.owlapi.search.EntitySearcher
import org.semanticweb.owlapi.model.parameters.Imports
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLClassExpression
import org.semanticweb.owlapi.model.OWLProperty
import org.semanticweb.owlapi.model.OWLDataProperty
import org.semanticweb.owlapi.model.OWLObjectProperty

abstract class BaseReasoner(ontology: OWLOntology) extends Reasoner {

	protected val factory = ontology.getOWLOntologyManager.getOWLDataFactory

	override def getSuperClasses(owlClass: OWLClass, direct: Boolean): Seq[OWLClassExpression] =
		if(direct)
			EntitySearcher.getSuperClasses(owlClass, ontology).toSeq
		else{
			val direct = getSuperClasses(owlClass, true)
			val transitive = direct.flatMap{
				case oc: OWLClass => getSuperClasses(oc, false)
				case _ => Nil
			}
			direct ++ transitive
		}

	override def isFunctional(prop: OWLProperty): Boolean = (prop match {
		case dp: OWLDataProperty => EntitySearcher.isFunctional(dp, ontology)
		case op: OWLObjectProperty => EntitySearcher.isFunctional(op, ontology)
		case _ => false
	}) || getParentProps(prop).exists(isFunctional)

	override def getPropertiesWhoseDomainIncludes(owlClass: OWLClass): Seq[OWLProperty] = {
		val dataProps = ontology.getDataPropertiesInSignature(Imports.EXCLUDED)
		val objProps = ontology.getObjectPropertiesInSignature(Imports.EXCLUDED)
		(dataProps.toSeq ++ objProps).collect{
			case p: OWLProperty if(isSubClass(owlClass, getFullDomain(p))) => p
		}
	}

	protected def getParentProps(owlProp: OWLProperty): Seq[OWLProperty]

	private def getOwnDomains(owlProp: OWLProperty): Seq[OWLClassExpression] = owlProp match {
		case dp: OWLDataProperty => EntitySearcher.getDomains(dp, ontology).toSeq
		case op: OWLObjectProperty => EntitySearcher.getDomains(op, ontology).toSeq
		case _ => Nil //ignoring annotation properties
	}

	private def getFullDomain(owlProp: OWLProperty): OWLClassExpression = {
		val domains: Seq[OWLClassExpression] =
			(getParentProps(owlProp) :+ owlProp).flatMap(getOwnDomains)
		domains.size match{
			case 0 => factory.getOWLNothing
			case 1 => domains.head
			case _ => factory.getOWLObjectIntersectionOf(domains.toSet)
		}
	}

}