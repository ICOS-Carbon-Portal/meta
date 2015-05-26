package se.lu.nateko.cp.meta.labeler

import scala.collection.JavaConversions._

import org.semanticweb.owlapi.search.EntitySearcher

import org.semanticweb.owlapi.model.OWLDataProperty
import org.semanticweb.owlapi.model.OWLDataFactory
import org.semanticweb.owlapi.model.OWLNamedIndividual
import org.semanticweb.owlapi.model.OWLOntology

class SinglePropIndividualLabeler(
	prop: OWLDataProperty,
	protected val factory: OWLDataFactory
) extends Labeler[OWLNamedIndividual] {

	override def getLabel(instance: OWLNamedIndividual, onto: OWLOntology): String = {

		val propVals: List[String] = EntitySearcher.getDataPropertyValues(instance, prop, onto)
			.toList
			.map(_.getLiteral)

		propVals match {
			case single :: Nil => single
			case Nil => super.getLabel(instance, onto)
			case multi => Labeler.joinMultiValues(multi)
		}
	}
}
