package se.lu.nateko.cp.meta.onto.labeler

import org.semanticweb.owlapi.model.{IRI => OwlIri}
import org.semanticweb.owlapi.model.OWLOntology
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.InstanceServerUtils
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.onto.labeler._
import org.eclipse.rdf4j.model.IRI

class UniversalLabeler(ontology: OWLOntology) extends InstanceLabeler{

	import scala.collection.mutable.Map
	private val cache: Map[IRI, InstanceLabeler] = Map()
	private[this] val owlFactory = ontology.getOWLOntologyManager.getOWLDataFactory

	override def getLabel(instUri: IRI, instServer: InstanceServer): String = {

		val theType: IRI = InstanceServerUtils.getSingleType(instUri, instServer)

		val theClass = owlFactory.getOWLClass(OwlIri.create(theType))

		val labeler = cache.getOrElseUpdate(theType, ClassIndividualsLabeler(theClass, ontology, this))

		labeler.getLabel(instUri, instServer)
	}

}
