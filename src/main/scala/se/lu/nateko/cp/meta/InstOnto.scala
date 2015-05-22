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

class InstOnto (resourcePath: String, onto: Onto, manager: OWLOntologyManager){

	private val factory = manager.getOWLDataFactory

	val ontology: OWLOntology = {
		val stream = getClass.getResourceAsStream(resourcePath)
		manager.loadOntologyFromOntologyDocument(stream)
	}

	def listInstances(classUri: URI): Seq[ResourceDto] = {
		val labeler = onto.getLabelerForClass(classUri)
		val owlClass = factory.getOWLClass(IRI.create(classUri))
		
		EntitySearcher
			.getIndividuals(owlClass, ontology)
			.collect{
				case named: OWLNamedIndividual => labeler.getInfo(named, ontology)
			}
			.toSeq
	}
}