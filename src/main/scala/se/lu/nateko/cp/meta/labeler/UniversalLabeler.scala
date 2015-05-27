package se.lu.nateko.cp.meta.labeler

import org.semanticweb.owlapi.model._
import se.lu.nateko.cp.meta.Utils

class UniversalLabeler(ontology: OWLOntology) extends Labeler[OWLNamedIndividual]{

	import scala.collection.mutable.Map
	private val cache: Map[OWLClass, Labeler[OWLNamedIndividual]] = Map()

	override def getLabel(ind: OWLNamedIndividual, instOnto: OWLOntology): String = {

		val theType: OWLClass = Utils.getSingleType(ind, instOnto)

		val labeler = cache.getOrElseUpdate(theType, ClassIndividualsLabeler(theType, ontology, this))

		labeler.getLabel(ind, instOnto)
	}
}