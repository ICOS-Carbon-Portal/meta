package se.lu.nateko.cp.meta

import java.net.URI
import scala.collection.JavaConversions._
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.search.EntitySearcher
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.OWLNamedIndividual
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration
import org.semanticweb.owlapi.io.StreamDocumentSource
import org.semanticweb.owlapi.model.OWLOntologyManager
import org.semanticweb.owlapi.model.OWLClass

class InstOnto (ontology: OWLOntology, onto: Onto){

	private val factory = ontology.getOWLOntologyManager.getOWLDataFactory

	def getIndividuals(classUri: URI): Seq[ResourceDto] = {
		val labeler = onto.getLabelerForClassIndividuals(classUri)
		val owlClass = factory.getOWLClass(IRI.create(classUri))
		
		EntitySearcher
			.getIndividuals(owlClass, ontology)
			.collect{
				case named: OWLNamedIndividual => labeler.getInfo(named, ontology)
			}
			.toSeq
	}

	def getIndividual(uri: URI): IndividualDto = {
		val labeler = onto.getUniversalLabeler
		val individual = factory.getOWLNamedIndividual(IRI.create(uri))

		val types = EntitySearcher.getTypes(individual, ontology).collect{
			case named: OWLClass => named.getIRI.toURI
		}.toSeq

		assert(types.size == 1, "Individuals must have exactly one type!")

		val classInfo = onto.getClassInfo(types.head)

		val litValues: Iterable[ValueDto] = ontology.getDataPropertyAssertionAxioms(individual)
			.toIterable
			.filter(axiom => !axiom.getProperty.isAnonymous)
			.map(axiom => LiteralValueDto(
				presentation = axiom.getObject.getLiteral,
				property = onto.rdfsLabeling(axiom.getProperty.asOWLDataProperty)
			))

		val objValues: Iterable[ValueDto] = ontology.getObjectPropertyAssertionAxioms(individual)
			.toIterable
			.filter(axiom => !axiom.getProperty.isAnonymous && axiom.getObject.isNamed)
			.map(axiom => ObjectValueDto(
				value = labeler.getInfo(axiom.getObject.asOWLNamedIndividual, ontology),
				property = onto.rdfsLabeling(axiom.getProperty.asOWLObjectProperty)
			))

		IndividualDto(
			resource = labeler.getInfo(individual, ontology),
			owlClass = classInfo,
			values = litValues.toSeq ++ objValues
		)
	}
}