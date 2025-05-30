package se.lu.nateko.cp.meta.onto.reasoner

import org.semanticweb.owlapi.model.{OWLClass, OWLClassExpression, OWLDataProperty, OWLObjectProperty, OWLOntology, OWLProperty}

import scala.jdk.CollectionConverters.IteratorHasAsScala


class HermitBasedReasoner(ontology: OWLOntology) extends BaseReasoner(ontology){

	val reasoner = new org.semanticweb.HermiT.ReasonerFactory().createReasoner(ontology)

	override def close(): Unit = {
		reasoner.dispose()
	}

	override def getSubClasses(owlClass: OWLClass, direct: Boolean): Seq[OWLClass] =
		reasoner.getSubClasses(owlClass, direct).entities.iterator.asScala.toSeq

	override def getTopLevelClasses: Seq[OWLClass] = reasoner
		.getSubClasses(factory.getOWLThing, true)
		.entities.iterator.asScala
		.toSeq

	override def isSubClass(subClass: OWLClassExpression, superClass: OWLClassExpression): Boolean = {
		val axiom = factory.getOWLSubClassOfAxiom(subClass, superClass)
		reasoner.isEntailed(axiom)
	}
	
	override protected def getParentProps(owlProp: OWLProperty): Seq[OWLProperty] = owlProp match {
		case dp: OWLDataProperty => reasoner.getSuperDataProperties(dp, false).entities.iterator.asScala.toIndexedSeq
		case op: OWLObjectProperty =>
			reasoner.getSuperObjectProperties(op, false).entities.iterator.asScala.collect{
				case op: OWLObjectProperty => op
			}.toIndexedSeq
		case _ => Nil //ignoring annotation properties
	}
}