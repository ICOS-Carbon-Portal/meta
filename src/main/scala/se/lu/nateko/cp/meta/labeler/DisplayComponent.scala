package se.lu.nateko.cp.meta.labeler

import org.semanticweb.owlapi.model._


sealed trait DisplayComponent

case class DataPropComponent(property: OWLDataProperty) extends DisplayComponent

case class ObjectPropComponent(property: OWLObjectProperty) extends DisplayComponent

case class ConstantComponent(value: String) extends DisplayComponent

object DisplayComponent{

	def apply(anno: OWLAnnotationValue, onto: OWLOntology): Option[DisplayComponent] = {

		val factory = onto.getOWLOntologyManager.getOWLDataFactory

		if(anno.asIRI.isPresent){
			val iri = anno.asIRI.get
			val dataProp = factory.getOWLDataProperty(iri)
			if(onto.isDeclared(dataProp)) Some(DataPropComponent(dataProp))
			else{
				val objProp = factory.getOWLObjectProperty(iri)
				if(onto.isDeclared(objProp)) Some(ObjectPropComponent(objProp))
				else None
			}
		} else if(anno.asLiteral.isPresent)
			Some(ConstantComponent(anno.asLiteral.get.getLiteral))
		else
			None
	}
}