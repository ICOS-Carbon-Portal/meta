package se.lu.nateko.cp.meta.onto.labeler

import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.search.EntitySearcher
import scala.jdk.CollectionConverters.*
import se.lu.nateko.cp.meta.onto.Vocab


object ClassIndividualsLabeler{

	def apply(owlClass: OWLClass, onto: OWLOntology, nested: InstanceLabeler): InstanceLabeler = {

		def getDisplayComponents(prop: OWLAnnotationProperty): Seq[DisplayComponent] =
			EntitySearcher
				.getAnnotations(owlClass, onto.importsClosure, prop).iterator.asScala
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
		else getSingleSuperClass(owlClass, onto.importsClosure) match{
			case Some(superClass) => apply(superClass, onto, nested)
			case None => Labeler.rdfs
		}
	}

	private def getSingleSuperClass(owlClass: OWLClass, ontologies: java.util.stream.Stream[OWLOntology]): Option[OWLClass] = {
		val superClasses = EntitySearcher.getSuperClasses(owlClass, ontologies).iterator.asScala
			.collect{case oc: OWLClass => oc}.toSeq

		assert(
			superClasses.size <= 1,
			s"Expected class ${owlClass.getIRI} to have at most one " +
				s"named superclass, but it had ${superClasses.size}"
		)

		superClasses.headOption
	}

}
