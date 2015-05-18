package se.lu.nateko.cp.meta

import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.search.EntitySearcher
import scala.collection.JavaConversions._
import org.semanticweb.owlapi.model.parameters.Imports
import Utils._
import org.semanticweb.owlapi.util.DefaultPrefixManager

class Onto (resourcePath: String){

	private[this] val manager = OWLManager.createOWLOntologyManager()
	private[this] val factory = manager.getOWLDataFactory

	val ontology: OWLOntology = {
		val stream = getClass.getResourceAsStream(resourcePath)
		manager.loadOntologyFromOntologyDocument(stream)
	}

	val defaultPrefix: Option[String] = {

		val explicitDefaultPrefix: Option[String] = {
			val format = manager.getOntologyFormat(ontology)
			if(format.isPrefixOWLOntologyFormat){
				val defaultPrefix = format.asPrefixOWLOntologyFormat.getDefaultPrefix
				if(defaultPrefix != null) Some(defaultPrefix)
				else None
			} else None
		}

		explicitDefaultPrefix.orElse{
			ontology.getOntologyID.getOntologyIRI.toOption.map(_.toString)
		}
	}

	def mentionedClasses: Seq[OWLClass] = {
		ontology.getClassesInSignature(Imports.EXCLUDED).toSeq
	}

	def lookupClass(localName: String): Option[ResourceInfo] = {
		for(
			prefix <- defaultPrefix;
			prefixManager = new DefaultPrefixManager(null, null, prefix);
			owlClass = factory.getOWLClass(localName, prefixManager);
			if(ontology.isDeclared(owlClass))
		) yield ResourceInfo(displayName = localName, uri = owlClass.getIRI.toURI.toString)
	}
}