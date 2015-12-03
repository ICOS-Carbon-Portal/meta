package se.lu.nateko.cp.meta.instanceserver

import org.openrdf.model.URI
import org.openrdf.model.vocabulary.RDF
import org.openrdf.model.vocabulary.OWL
import org.openrdf.model.Literal
import scala.util.Try

object InstanceServerUtils {

	/**
	 * returns Some type URI if any, None if none, and throws an AssertionError if the instance has more than one type.
	 * The type owl:NamedIndividual is disregarded.
	 */
	def getSingleTypeIfAny(instUri: URI, instServer: InstanceServer): Option[URI] = {
		val namedIndivid = instServer.factory.createURI(OWL.NAMESPACE, "NamedIndividual")

		val types = instServer.getValues(instUri, RDF.TYPE).collect{
			case classUri: URI if classUri != namedIndivid => classUri
		}

		assert(types.size <= 1, s"Expected individual $instUri to have at most one type, but it had ${types.size}")
		types.headOption
	}

	def getSingleType(instUri: URI, instServer: InstanceServer): URI = {
		val typeIfAny = getSingleTypeIfAny(instUri, instServer)
		assert(typeIfAny.isDefined, s"Instance $instUri has no type")
		typeIfAny.get
	}

	def getSingleLitValue(instUri: URI, prop: URI, instServer: InstanceServer): Literal = {
		val lits = instServer.getValues(instUri, prop).collect{case lit: Literal => lit}

		assert(lits.size == 1, s"Expected exactly one literal value of $prop for $instUri, but got ${lits.size}")

		lits.head
	}

}