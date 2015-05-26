package se.lu.nateko.cp.meta.labeler

import scala.collection.JavaConversions._
import org.semanticweb.owlapi.search.EntitySearcher
import org.semanticweb.owlapi.model._

class MultiComponentIndividualLabeler(
	components: Seq[DisplayComponent],
	inner: OWLNamedIndividual => String,
	protected val factory: OWLDataFactory
) extends Labeler[OWLNamedIndividual] {


	private val compMakers = components.map{
		case DataPropComponent(prop) => Labeler.singleProp(prop).getLabel _
		case ObjectPropComponent(prop) => getComponent(prop) _
		case ConstantComponent(value) => (ind: OWLNamedIndividual, ont: OWLOntology) => value
	}

	override def getLabel(instance: OWLNamedIndividual, onto: OWLOntology): String = {
		val labelComponents = compMakers.map(_(instance, onto))
		labelComponents.mkString(" ")
	}

	private def getComponent(prop: OWLObjectProperty)(ind: OWLNamedIndividual, ont: OWLOntology): String = {
		val values = EntitySearcher.getObjectPropertyValues(ind, prop, ont).collect{
			case named: OWLNamedIndividual => inner(named)
		}
		Labeler.joinMultiValues(values)
	}
}
