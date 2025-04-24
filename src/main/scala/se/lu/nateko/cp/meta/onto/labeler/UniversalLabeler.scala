package se.lu.nateko.cp.meta.onto.labeler

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.IRI
import org.semanticweb.owlapi.model.{IRI as OwlIri, OWLOntology}
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.onto.InstOnto
import se.lu.nateko.cp.meta.utils.rdf4j.*

import scala.collection.mutable

class UniversalLabeler(ontology: OWLOntology) extends InstanceLabeler:

	private val cache = mutable.Map.empty[IRI, InstanceLabeler]
	private val owlFactory = ontology.getOWLOntologyManager.getOWLDataFactory

	override def getLabel(instUri: IRI)(using StatementSource): String =
		try
			val theType: IRI = InstOnto.getSingleType(instUri)

			val theClass = owlFactory.getOWLClass(OwlIri.create(theType.toJava))

			val labeler = cache.getOrElseUpdate(theType, ClassIndividualsLabeler(theClass, ontology, this))

			labeler.getLabel(instUri)

		catch case _: Throwable =>
				super.getLabel(instUri)
