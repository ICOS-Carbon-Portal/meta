package se.lu.nateko.cp.meta

import java.net.URI
import scala.collection.JavaConversions._
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.search.EntitySearcher
import org.semanticweb.owlapi.io.StreamDocumentSource
import org.semanticweb.owlapi.model._

class InstOnto (ontology: OWLOntology, onto: Onto){

	private val factory = ontology.getOWLOntologyManager.getOWLDataFactory


	def getIndividuals(classUri: URI): Seq[ResourceDto] = {

		val labeler = onto.getLabelerForClassIndividuals(classUri)

		def getForClass(owlClass: OWLClass): Seq[ResourceDto] = {
			EntitySearcher
				.getIndividuals(owlClass, ontology)
				.collect{
					case named: OWLNamedIndividual => labeler.getInfo(named, ontology)
				}
				.toSeq
		}

		val owlClass = factory.getOWLClass(IRI.create(classUri))
		val ownIndividuals = getForClass(owlClass)
		val subclassIndividuals = onto.getSubClasses(classUri, false).flatMap(getForClass)

		(ownIndividuals ++ subclassIndividuals).distinct
	}

	def getIndividual(uri: URI): IndividualDto = {
		val labeler = onto.getUniversalLabeler
		val individual = factory.getOWLNamedIndividual(IRI.create(uri))

		val theType: OWLClass = Utils.getSingleType(individual, ontology)
		val classInfo = onto.getClassInfo(theType.getIRI.toURI)

		val litValues: Iterable[ValueDto] = ontology.getDataPropertyAssertionAxioms(individual)
			.toIterable
			.filter(axiom => !axiom.getProperty.isAnonymous)
			.map(axiom => LiteralValueDto(
				value = axiom.getObject.getLiteral,
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