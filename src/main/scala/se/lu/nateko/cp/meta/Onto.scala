package se.lu.nateko.cp.meta

import java.net.URI
import scala.collection.JavaConversions._
import Utils._
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.AxiomType
import org.semanticweb.owlapi.model.OWLNamedIndividual
import org.semanticweb.owlapi.search.EntitySearcher
import org.semanticweb.owlapi.model.OWLDataProperty
import org.semanticweb.owlapi.model.OWLEntity
import org.semanticweb.owlapi.model.OWLOntologyManager

class Onto (resourcePath: String, manager: OWLOntologyManager){

	private val factory = manager.getOWLDataFactory

	val ontology: OWLOntology = {
		val stream = getClass.getResourceAsStream(resourcePath)
		manager.loadOntologyFromOntologyDocument(stream)
	}

	private val rdfsLabeling: OWLEntity => ResourceInfo =
		Labeler.rdfs.getInfo(_, ontology)

	def getExposedClasses: Seq[ResourceInfo] =
		ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION).toIterable
			.filter(axiom =>
				axiom.getProperty == Vocab.exposedToUsersAnno && {
					axiom.getValue.asLiteral.toOption match {
						case Some(owlLit) if(owlLit.isBoolean) => owlLit.parseBoolean
						case _ => false
					}
				}
			)
			.map(_.getSubject)
			.collect{case iri: IRI => factory.getOWLClass(iri)}
			.map(rdfsLabeling)
			.toSeq
			.distinct

	//TODO Cache this method
	def getLabelerForClass(classUri: URI): Labeler[OWLNamedIndividual] = {
		val owlClass = factory.getOWLClass(IRI.create(classUri))

		val displayProp: Option[OWLDataProperty] =
			EntitySearcher.getAnnotations(owlClass, ontology, Vocab.displayPropAnno)
				.toIterable
				.map(_.getValue.asIRI.toOption)
				.collect{case Some(iri) => factory.getOWLDataProperty(iri)}
				.filter(ontology.isDeclared)
				.headOption

		displayProp match{
			case Some(prop) => Labeler.singleProp(prop)
			case None => Labeler.rdfs
		}
	}

}