package se.lu.nateko.cp.meta.labeler

import se.lu.nateko.cp.meta._
import Utils._

import scala.collection.JavaConversions._
import org.semanticweb.owlapi.model._
import org.semanticweb.owlapi.search.EntitySearcher


object SingleClassLabeler{

	def apply(owlClass: OWLClass, onto: OWLOntology): Labeler[OWLNamedIndividual] = {

		val factory = getFactory(onto)
		
		val mainDisplayProp: Seq[OWLProperty] =
			EntitySearcher.getAnnotations(owlClass, onto, Vocab.displayPropAnno)
				.map(_.getValue.asIRI.toOption.flatMap(getOwlProp(_, onto)))
				.flatten
				.toList
		mainDisplayProp match{
			case (prop: OWLDataProperty) :: Nil => Labeler.singleProp(prop)
			case (prop: OWLObjectProperty) :: Nil => ???
			case Nil => ???
			case _ => throw new Exception("Encountered multiple main display properties for " + Utils.getLastFragment(owlClass.getIRI))
		}
	}

	private def getFactory(onto: OWLOntology) = onto.getOWLOntologyManager.getOWLDataFactory
	
	private def getOwlProp(iri: IRI, onto: OWLOntology): Option[OWLProperty] = {
		val factory = getFactory(onto)
		val dataProp = factory.getOWLDataProperty(iri)
		if(onto.isDeclared(dataProp)) Some(dataProp)
		else{
			val objProp = factory.getOWLObjectProperty(iri)
			if(onto.isDeclared(objProp)) Some(objProp)
			else None
		}
	}

}