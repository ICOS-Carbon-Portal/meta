package se.lu.nateko.cp.meta.labeler

import org.semanticweb.owlapi.model.OWLDataProperty
import org.semanticweb.owlapi.model.OWLObjectProperty


sealed trait DisplayComponent

case class DataPropComponent(property: OWLDataProperty) extends DisplayComponent

case class ObjectPropComponent(property: OWLObjectProperty) extends DisplayComponent

case class ConstantComponent(value: String) extends DisplayComponent