package se.lu.nateko.cp.meta

import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.search.EntitySearcher
import scala.collection.JavaConversions._
import org.semanticweb.owlapi.model.parameters.Imports

class Onto (resourcePath: String){

	private[this] val manager = OWLManager.createOWLOntologyManager()
	
	val ontology: OWLOntology = {
		val stream = getClass.getResourceAsStream(resourcePath)
		manager.loadOntologyFromOntologyDocument(stream)
	}

	def mentionedClasses: Seq[OWLClass] = {
		ontology.getClassesInSignature(Imports.EXCLUDED).toSeq
	}
}