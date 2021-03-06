package se.lu.nateko.cp.meta.onto.labeler

import org.semanticweb.owlapi.model.{IRI => OWLIRI, _}
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import org.eclipse.rdf4j.model.Literal

class MultiComponentIndividualLabeler(
	components: Seq[DisplayComponent],
	inner: InstanceLabeler
) extends InstanceLabeler {


	private val compMakers = components.map{
		case DataPropComponent(prop) => getComponent(prop) _
		case ObjectPropComponent(prop) => getComponent(prop) _
		case ConstantComponent(value) => (_: IRI, _: InstanceServer) => value
	}

	override def getLabel(instUri: IRI, instServer: InstanceServer): String = {

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

	private def getComponent(propIri: OWLIRI)(instUri: IRI, instServer: InstanceServer): String = {
		val propUri = toUri(propIri, instServer)
		val values = instServer.getValues(instUri, propUri).collect{
			case literal: Literal => literal.getLabel
		}
		Labeler.joinMultiValues(values)
	}

	private def getComponent(prop: OWLObjectProperty)(instUri: IRI, instServer: InstanceServer): String = {
		val propUri = toUri(prop.getIRI, instServer)
		val values = instServer.getValues(instUri, propUri).collect{
			case uri: IRI => inner.getLabel(uri, instServer)
		}
		Labeler.joinMultiValues(values)
	}

	private def toUri(prop: OWLIRI, instServer: InstanceServer): IRI =
		instServer.factory.createIRI(prop.toURI.toString)
}
