package se.lu.nateko.cp.meta.onto.reasoner

import scala.language.unsafeNulls

import org.semanticweb.owlapi.model.parameters.Imports
import org.semanticweb.owlapi.model.{OWLClass, OWLClassExpression, OWLDataProperty, OWLObjectProperty, OWLOntology, OWLProperty}
import org.semanticweb.owlapi.search.EntitySearcher

import scala.jdk.CollectionConverters.{IteratorHasAsScala, SeqHasAsJava}

abstract class BaseReasoner(ontology: OWLOntology) extends Reasoner {

	protected val factory = ontology.getOWLOntologyManager.getOWLDataFactory
	private def ontologies = ontology.importsClosure

	override def getSuperClasses(owlClass: OWLClass, direct: Boolean): Seq[OWLClassExpression] =
		if(direct)
			EntitySearcher.getSuperClasses(owlClass, ontologies).iterator.asScala.toIndexedSeq
		else{
			val direct = getSuperClasses(owlClass, true)
			val transitive = direct.flatMap{
				case oc: OWLClass => getSuperClasses(oc, false)
				case _ => Nil
			}
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
		val dataProps = ontology.dataPropertiesInSignature(Imports.INCLUDED).iterator.asScala
		val objProps = ontology.objectPropertiesInSignature(Imports.INCLUDED).iterator.asScala
		(dataProps ++ objProps).collect{
			case p: OWLProperty if(isSubClass(owlClass, getFullDomain(p))) => p
		}.toIndexedSeq
	}

	protected def getParentProps(owlProp: OWLProperty): Seq[OWLProperty]

	private def getOwnDomains(owlProp: OWLProperty): Seq[OWLClassExpression] = owlProp match {
		case dp: OWLDataProperty => EntitySearcher.getDomains(dp, ontologies).iterator.asScala.toIndexedSeq
		case op: OWLObjectProperty => EntitySearcher.getDomains(op, ontologies).iterator.asScala.toIndexedSeq
		case _ => Nil //ignoring annotation properties
	}

	private def getFullDomain(owlProp: OWLProperty): OWLClassExpression = {
		val domains: Seq[OWLClassExpression] =
			(getParentProps(owlProp) :+ owlProp).flatMap(getOwnDomains).filterNot(_.isOWLThing)
		domains.size match{
			case 0 => factory.getOWLNothing
			case 1 => domains.head
			case _ => factory.getOWLObjectUnionOf(domains.asJava)
		}
	}

}
