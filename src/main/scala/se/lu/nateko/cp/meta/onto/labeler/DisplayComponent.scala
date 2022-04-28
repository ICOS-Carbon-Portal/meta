package se.lu.nateko.cp.meta.onto.labeler

import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.model.parameters.Imports


sealed trait DisplayComponent

case class DataPropComponent(property: IRI) extends DisplayComponent

case class ObjectPropComponent(property: OWLObjectProperty) extends DisplayComponent

case class ConstantComponent(value: String) extends DisplayComponent

object DisplayComponent{

	def apply(anno: OWLAnnotationValue, onto: OWLOntology): Option[DisplayComponent] = {

		val factory = onto.getOWLOntologyManager.getOWLDataFactory

		if(anno.asIRI.isPresent){
			val iri = anno.asIRI.get
			val objProp = factory.getOWLObjectProperty(iri)
			if(onto.isDeclared(objProp, Imports.INCLUDED)) Some(ObjectPropComponent(objProp))
			else Some(DataPropComponent(iri))
		} else if(anno.asLiteral.isPresent)
			Some(ConstantComponent(anno.asLiteral.get.getLiteral))
		else
			None
	}
}
