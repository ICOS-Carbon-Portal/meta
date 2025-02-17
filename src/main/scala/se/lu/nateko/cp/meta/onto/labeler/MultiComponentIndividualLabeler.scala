package se.lu.nateko.cp.meta.onto.labeler

import org.eclipse.rdf4j.model.{IRI, Literal}
import org.semanticweb.owlapi.model.{IRI as OWLIRI, OWLObjectProperty}
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.{TSC, getValues}

class MultiComponentIndividualLabeler(
	components: Seq[DisplayComponent],
	inner: InstanceLabeler
) extends InstanceLabeler:


	private val compMakers: Seq[IRI => TSC ?=> String] = components.map{
		case DataPropComponent(prop) => getComponent(prop)
		case ObjectPropComponent(prop) => getComponent(prop)
		case ConstantComponent(value) => (_: IRI) => (_: TSC) ?=> value
	}

	override def getLabel(instUri: IRI)(using TSC): String = {

		val labelComponents = compMakers.map(_(instUri))

		val nonEmptyExists: Boolean = labelComponents.zip(components).exists{
			case (_, ConstantComponent(_)) => false
			case (label, _) => label.nonEmpty
		}

		if(nonEmptyExists)
			Labeler.joinComponents(labelComponents)
		else
			super.getLabel(instUri)
	}

	private def getComponent(propIri: OWLIRI)(instUri: IRI)(using TSC): String =
		val propUri = toUri(propIri)
		val values = getValues(instUri, propUri).collect:
			case literal: Literal => literal.getLabel
		Labeler.joinMultiValues(values)


	private def getComponent(prop: OWLObjectProperty)(instUri: IRI)(using TSC): String =
		val propUri = toUri(prop.getIRI)
		val values = getValues(instUri, propUri).collect:
			case uri: IRI => inner.getLabel(uri)

		Labeler.joinMultiValues(values)

	private def toUri(prop: OWLIRI)(using conn: TSC): IRI =
		conn.factory.createIRI(prop.toURI.toString)

end MultiComponentIndividualLabeler
