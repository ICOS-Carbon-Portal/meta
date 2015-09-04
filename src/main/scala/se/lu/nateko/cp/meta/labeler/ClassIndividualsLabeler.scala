package se.lu.nateko.cp.meta.labeler

import se.lu.nateko.cp.meta._
import utils.owlapi._

import scala.collection.JavaConversions._
import org.semanticweb.owlapi.model._
import org.semanticweb.owlapi.search.EntitySearcher


object ClassIndividualsLabeler{

	def apply(owlClass: OWLClass, onto: OWLOntology, nested: InstanceLabeler): InstanceLabeler = {

		val ontologies = onto.getImportsClosure

		def getDisplayComponents(prop: OWLAnnotationProperty): Seq[DisplayComponent] =
			EntitySearcher
				.getAnnotations(owlClass, ontologies, prop)
				.map{anno => DisplayComponent(anno.getValue, onto)}
				.flatten
				.toSeq

		val mainDisplayComps = getDisplayComponents(Vocab.displayPropAnno)

		val displayComps =
			if(mainDisplayComps.nonEmpty)
				mainDisplayComps
			else
				Vocab.displayPropAnnos.flatMap(getDisplayComponents)

		if(displayComps.nonEmpty)
			new MultiComponentIndividualLabeler(displayComps, nested)
		else getSingleSuperClass(owlClass, ontologies) match{
			case Some(superClass) => apply(superClass, onto, nested)
			case None => Labeler.rdfs
		}
	}

	private def getSingleSuperClass(owlClass: OWLClass, ontologies: java.util.Set[OWLOntology]): Option[OWLClass] = {
		val superClasses = EntitySearcher.getSuperClasses(owlClass, ontologies)
			.collect{case oc: OWLClass => oc}.toSeq

		assert(
			superClasses.size <= 1,
			s"Expected class ${owlClass.getIRI} to have at most one " +
				s"named superclass, but it had ${superClasses.size}"
		)

		superClasses.headOption
	}

}