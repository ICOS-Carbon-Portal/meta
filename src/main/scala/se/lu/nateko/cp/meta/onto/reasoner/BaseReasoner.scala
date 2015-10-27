package se.lu.nateko.cp.meta.onto.reasoner

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
	private val ontologies = ontology.getImportsClosure

	override def getSuperClasses(owlClass: OWLClass, direct: Boolean): Seq[OWLClassExpression] =
		if(direct)
			EntitySearcher.getSuperClasses(owlClass, ontologies).toSeq
		else{
			val direct = getSuperClasses(owlClass, true)
			val transitive = direct.flatMap{
				case oc: OWLClass => getSuperClasses(oc, false)
				case _ => Nil
			}
			direct ++ transitive
		}

	override def getSubClasses(owlClass: OWLClass, direct: Boolean): Seq[OWLClass] =
		if(direct) EntitySearcher
			.getSubClasses(owlClass, ontologies)
			.collect{case oc: OWLClass => oc}
			.toSeq
		else{
			val direct = getSubClasses(owlClass, true)
			val transitive = direct.flatMap(getSubClasses(_, false))
			direct ++ transitive
		}

	override def isFunctional(prop: OWLProperty): Boolean = {
		def isFunctionalSelf(prop: OWLProperty): Boolean = prop match {
			case dp: OWLDataProperty => EntitySearcher.isFunctional(dp, ontologies)
			case op: OWLObjectProperty => EntitySearcher.isFunctional(op, ontologies)
			case _ => false
		}
		isFunctionalSelf(prop) || getParentProps(prop).exists(isFunctionalSelf)
	}

	override def getPropertiesWhoseDomainIncludes(owlClass: OWLClass): Seq[OWLProperty] = {
		val dataProps = ontology.getDataPropertiesInSignature(Imports.INCLUDED)
		val objProps = ontology.getObjectPropertiesInSignature(Imports.INCLUDED)
		(dataProps.toSeq ++ objProps).collect{
			case p: OWLProperty if(isSubClass(owlClass, getFullDomain(p))) => p
		}
	}

	protected def getParentProps(owlProp: OWLProperty): Seq[OWLProperty]

	private def getOwnDomains(owlProp: OWLProperty): Seq[OWLClassExpression] = owlProp match {
		case dp: OWLDataProperty => EntitySearcher.getDomains(dp, ontologies).toSeq
		case op: OWLObjectProperty => EntitySearcher.getDomains(op, ontologies).toSeq
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