package se.lu.nateko.cp.meta.reasoner

import scala.collection.JavaConversions._
import se.lu.nateko.cp.meta.utils.owlapi._
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLProperty
import org.semanticweb.owlapi.model.OWLClassExpression
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.parameters.Imports
import org.semanticweb.owlapi.model.OWLDataProperty
import org.semanticweb.owlapi.model.OWLObjectProperty
import org.semanticweb.owlapi.search.EntitySearcher


class HermitBasedReasoner(ontology: OWLOntology) extends BaseReasoner(ontology){
	
	val reasoner = new org.semanticweb.HermiT.Reasoner.ReasonerFactory()
		.createReasoner(ontology)
	override def close(): Unit = {
		reasoner.dispose()
	}

	override def getTopLevelClasses: Seq[OWLClass] = reasoner
		.getSubClasses(factory.getOWLThing, true)
		.getFlattened
		.toSeq

	override def isSubClass(subClass: OWLClassExpression, superClass: OWLClassExpression): Boolean = {
		val axiom = factory.getOWLSubClassOfAxiom(subClass, superClass)
		reasoner.isEntailed(axiom)
	}
	
	override protected def getParentProps(owlProp: OWLProperty): Seq[OWLProperty] = owlProp match {
		case dp: OWLDataProperty => reasoner.getSuperDataProperties(dp, false).getFlattened.toSeq
		case op: OWLObjectProperty =>
			reasoner.getSuperObjectProperties(op, false).getFlattened.toSeq.collect{
				case op: OWLObjectProperty => op
			}
		case _ => Nil //ignoring annotation properties
	}
}