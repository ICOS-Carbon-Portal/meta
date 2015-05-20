package se.lu.nateko.cp.meta

import scala.collection.JavaConversions._
import Utils._
import org.semanticweb.owlapi.model.OWLEntity
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.OWLNamedIndividual
import org.semanticweb.owlapi.search.EntitySearcher
import org.semanticweb.owlapi.model.OWLDataFactory
import org.semanticweb.owlapi.io.XMLUtils
import org.semanticweb.owlapi.model.OWLDataProperty
import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl

trait Labeler[-T <: OWLEntity] {

	protected def factory: OWLDataFactory

	// rdfs:label is the default, to be overridden in some implementations
	def getLabel(entity: T, onto: OWLOntology): String =
		getRdfsLabel(entity, onto).getOrElse(XMLUtils.getNCNameSuffix(entity.getIRI.toString))

	final def getRdfsLabel(entity: T, onto: OWLOntology): Option[String] = EntitySearcher
		.getAnnotations(entity, onto, factory.getRDFSLabel)
		.toIterable
		.map(_.getValue.asLiteral.toOption)
		.collect{case Some(lit) => lit.getLiteral}
		.headOption

	final def getInfo(entity: T, onto: OWLOntology) = ResourceInfo(
		displayName = getLabel(entity, onto),
		uri = entity.getIRI.toURI
	)
}

object Labeler{
	private val factory: OWLDataFactory = new OWLDataFactoryImpl(true, false)

	val rdfs = new RdfsLabeler(factory)

	def singleProp(prop: OWLDataProperty) =
		new SinglePropIndividualLabeler(prop, factory)
}

class RdfsLabeler(protected val factory: OWLDataFactory) extends Labeler[OWLEntity]

class SinglePropIndividualLabeler(
	prop: OWLDataProperty,
	protected val factory: OWLDataFactory
) extends Labeler[OWLNamedIndividual] {

	override def getLabel(instance: OWLNamedIndividual, onto: OWLOntology): String = {

		val propVals: List[String] = EntitySearcher.getDataPropertyValues(instance, prop, onto)
			.toList
			.map(_.getLiteral)

		propVals match {
			case single :: Nil => single
			case Nil => super.getLabel(instance, onto)
			case multi => multi.mkString("{", ",", "}")
		}
	}
}
