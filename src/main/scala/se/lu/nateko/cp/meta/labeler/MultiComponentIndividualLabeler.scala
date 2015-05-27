package se.lu.nateko.cp.meta.labeler

import scala.collection.JavaConversions._
import org.semanticweb.owlapi.search.EntitySearcher
import org.semanticweb.owlapi.model._

class MultiComponentIndividualLabeler(
	components: Seq[DisplayComponent],
	inner: Labeler[OWLNamedIndividual]
) extends Labeler[OWLNamedIndividual] {


	private val compMakers = components.map{
		case DataPropComponent(prop) => getComponent(prop) _
		case ObjectPropComponent(prop) => getComponent(prop) _
		case ConstantComponent(value) => (ind: OWLNamedIndividual, ont: OWLOntology) => value
	}

	override def getLabel(individual: OWLNamedIndividual, onto: OWLOntology): String = {

		val labelComponents = compMakers.map(_(individual, onto))

		if(labelComponents.exists(_.nonEmpty))
			Labeler.joinComponents(labelComponents)
		else
			super.getLabel(individual, onto)
	}

	private def getComponent(prop: OWLObjectProperty)(ind: OWLNamedIndividual, ont: OWLOntology): String = {
		val values = EntitySearcher.getObjectPropertyValues(ind, prop, ont).collect{
			case named: OWLNamedIndividual => inner.getLabel(named, ont)
		}
		Labeler.joinMultiValues(values)
	}

	private def getComponent(prop: OWLDataProperty)(ind: OWLNamedIndividual, ont: OWLOntology): String = {
		val values = EntitySearcher.getDataPropertyValues(ind, prop, ont).map(_.getLiteral)
		Labeler.joinMultiValues(values)
	}
}
