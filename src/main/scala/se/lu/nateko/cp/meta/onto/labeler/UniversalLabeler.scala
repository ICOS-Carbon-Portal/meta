package se.lu.nateko.cp.meta.onto.labeler

import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.OWLOntology
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.onto.labeler._
import org.openrdf.model.URI

class UniversalLabeler(ontology: OWLOntology) extends InstanceLabeler{

	import scala.collection.mutable.Map
	private val cache: Map[URI, InstanceLabeler] = Map()
	private[this] val owlFactory = ontology.getOWLOntologyManager.getOWLDataFactory

	override def getLabel(instUri: URI, instServer: InstanceServer): String = {

		val theType: URI = LabelerHelpers.getSingleType(instUri.toJava, instServer)

		val theClass = owlFactory.getOWLClass(IRI.create(theType.toJava))

		val labeler = cache.getOrElseUpdate(theType, ClassIndividualsLabeler(theClass, ontology, this))

		labeler.getLabel(instUri, instServer)
	}

}
