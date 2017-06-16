package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.Literal
import scala.util.Try

object InstanceServerUtils {

	/**
	 * returns Some type IRI if any, None if none, and throws an AssertionError if the instance has more than one type.
	 * The type owl:NamedIndividual is disregarded.
	 */
	def getSingleTypeIfAny(instUri: IRI, instServer: InstanceServer): Option[IRI] = {
		val namedIndivid = instServer.factory.createIRI(OWL.NAMESPACE, "NamedIndividual")

		val types = instServer.getValues(instUri, RDF.TYPE).collect{
			case classUri: IRI if classUri != namedIndivid => classUri
		}

		assert(types.size <= 1, s"Expected individual $instUri to have at most one type, but it had ${types.size}")
		types.headOption
	}

	def getSingleType(instUri: IRI, instServer: InstanceServer): IRI = {
		val typeIfAny = getSingleTypeIfAny(instUri, instServer)
		assert(typeIfAny.isDefined, s"Instance $instUri has no type")
		typeIfAny.get
	}

	def getSingleLitValue(instUri: IRI, prop: IRI, instServer: InstanceServer): Literal = {
		val lits = instServer.getValues(instUri, prop).collect{case lit: Literal => lit}

		assert(lits.size == 1, s"Expected exactly one literal value of $prop for $instUri, but got ${lits.size}")

		lits.head
	}

}