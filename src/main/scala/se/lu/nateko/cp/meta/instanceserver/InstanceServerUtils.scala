package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.OWL

object InstanceServerUtils {

	/**
	 * returns Some type IRI if any, None if none, and throws an AssertionError if the instance has more than one type.
	 * The type owl:NamedIndividual is disregarded.
	 */
	def getSingleTypeIfAny(instUri: IRI, instServer: InstanceServer): Option[IRI] = {

		val types = instServer.getTypes(instUri).filter(_ != OWL.NAMEDINDIVIDUAL).distinct

		assert(types.size <= 1, s"Expected individual $instUri to have at most one type, but it had ${types.size}")
		types.headOption
	}

	def getSingleType(instUri: IRI, instServer: InstanceServer): IRI = {
		val typeIfAny = getSingleTypeIfAny(instUri, instServer)
		assert(typeIfAny.isDefined, s"Instance $instUri has no type")
		typeIfAny.get
	}

}
