package se.lu.nateko.cp.meta.labeler

import scala.collection.JavaConversions._
import se.lu.nateko.cp.meta._
import Utils._

import org.semanticweb.owlapi.search.EntitySearcher

import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl

import org.semanticweb.owlapi.model._

trait Labeler[-T <: OWLEntity] {

	// rdfs:label is the default, to be overridden in some implementations
	def getLabel(entity: T, onto: OWLOntology): String =
		getRdfsLabel(entity, onto).getOrElse(Utils.getLastFragment(entity.getIRI))

	protected def getFactory(onto: OWLOntology) = onto.getOWLOntologyManager.getOWLDataFactory

	final def getRdfsLabel(entity: T, onto: OWLOntology): Option[String] = EntitySearcher
		.getAnnotations(entity, onto, getFactory(onto).getRDFSLabel)
		.toIterable
		.map(_.getValue.asLiteral.toOption)
		.collect{case Some(lit) => lit.getLiteral}
		.headOption

	final def getInfo(entity: T, onto: OWLOntology) = ResourceDto(
		displayName = getLabel(entity, onto),
		uri = entity.getIRI.toURI
	)
}

object Labeler{

	object rdfs extends Labeler[OWLEntity]{}

	def joinMultiValues(values: Iterable[String]): String = values.toList match{
		case only :: Nil => only
		case Nil => ""
		case _ => values.mkString("{", ",", "}")
	}

	def joinComponents(values: Iterable[String]): String = values.mkString(" ")
}

