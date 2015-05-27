package se.lu.nateko.cp.meta.labeler

import se.lu.nateko.cp.meta._
import Utils._

import scala.collection.JavaConversions._
import org.semanticweb.owlapi.model._
import org.semanticweb.owlapi.search.EntitySearcher


object ClassIndividualsLabeler{

	def apply(owlClass: OWLClass, onto: OWLOntology, nested: UniversalLabeler): Labeler[OWLNamedIndividual] = {

		def getDisplayComponents(prop: OWLAnnotationProperty): Seq[DisplayComponent] =
			EntitySearcher
				.getAnnotations(owlClass, onto, prop)
				.map{anno => DisplayComponent(anno.getValue, onto)}
				.flatten
				.toSeq

		val mainDisplayComps = getDisplayComponents(Vocab.displayPropAnno)

		val displayComps =
			if(mainDisplayComps.nonEmpty)
				mainDisplayComps
			else
				Vocab.displayPropAnnos.flatMap(getDisplayComponents)

		new MultiComponentIndividualLabeler(displayComps, nested)
	}


}