package se.lu.nateko.cp.meta.labeler

import scala.collection.JavaConversions._
import se.lu.nateko.cp.meta._
import Utils._

import org.semanticweb.owlapi.search.EntitySearcher

import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl

import org.semanticweb.owlapi.model._

trait Labeler[-T <: OWLEntity] {

	protected def factory: OWLDataFactory

	// rdfs:label is the default, to be overridden in some implementations
	def getLabel(entity: T, onto: OWLOntology): String =
		getRdfsLabel(entity, onto).getOrElse(Utils.getLastFragment(entity.getIRI))

	final def getRdfsLabel(entity: T, onto: OWLOntology): Option[String] = EntitySearcher
		.getAnnotations(entity, onto, factory.getRDFSLabel)
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
	private val factory: OWLDataFactory = new OWLDataFactoryImpl(true, false)

	val rdfs = new RdfsLabeler(factory)

	def singleProp(prop: OWLDataProperty) =
		new SinglePropIndividualLabeler(prop, factory)

	def joinMultiValues(values: Iterable[String]) = values.mkString("{", ",", "}")
}

class RdfsLabeler(protected val factory: OWLDataFactory) extends Labeler[OWLEntity]

