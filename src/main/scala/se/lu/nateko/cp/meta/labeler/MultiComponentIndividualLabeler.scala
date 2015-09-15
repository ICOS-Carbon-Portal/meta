package se.lu.nateko.cp.meta.labeler

import scala.collection.JavaConversions._
import org.semanticweb.owlapi.search.EntitySearcher
import org.semanticweb.owlapi.model._
import org.openrdf.model.URI
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import org.openrdf.model.Literal

class MultiComponentIndividualLabeler(
	components: Seq[DisplayComponent],
	inner: InstanceLabeler
) extends InstanceLabeler {


	private val compMakers = components.map{
		case DataPropComponent(prop) => getComponent(prop) _
		case ObjectPropComponent(prop) => getComponent(prop) _
		case ConstantComponent(value) => (instUri: URI, instServer: InstanceServer) => value
	}

	override def getLabel(instUri: URI, instServer: InstanceServer): String = {

		val labelComponents = compMakers.map(_(instUri, instServer))

		val nonEmptyExists: Boolean = labelComponents.zip(components).exists{
			case (_, ConstantComponent(_)) => false
			case (label, _) => label.nonEmpty
		}

		if(nonEmptyExists)
			Labeler.joinComponents(labelComponents)
		else
			super.getLabel(instUri, instServer)
	}

	private def getComponent(prop: OWLDataProperty)(instUri: URI, instServer: InstanceServer): String = {
		val propUri = toUri(prop, instServer)
		val values = instServer.getValues(instUri, propUri).collect{
			case literal: Literal => literal.getLabel
		}
		Labeler.joinMultiValues(values)
	}

	private def getComponent(prop: OWLObjectProperty)(instUri: URI, instServer: InstanceServer): String = {
		val propUri = toUri(prop, instServer)
		val values = instServer.getValues(instUri, propUri).collect{
			case uri: URI => inner.getLabel(uri, instServer)
		}
		Labeler.joinMultiValues(values)
	}

	private def toUri(prop: OWLProperty, instServer: InstanceServer): URI =
		instServer.factory.createURI(prop.getIRI.toURI.toString)
}
