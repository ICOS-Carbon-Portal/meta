package se.lu.nateko.cp.meta.ingestion

import org.eclipse.rdf4j.model.{BNode, IRI, Statement, ValueFactory}
import scala.collection.mutable
import se.lu.nateko.cp.meta.utils.rdf4j.createIRI

class BnodeStabilizers:

	private val stabs = mutable.Map.empty[IRI, BnodeStabilizer]

	def getStabilizer(rdfGraph: IRI): BnodeStabilizer = synchronized:
		stabs.getOrElseUpdate(rdfGraph, BnodeStabilizer(rdfGraph))

class BnodeStabilizer(graph: IRI):
	private var i: Int = 0
	private val nodeLookup = mutable.Map.empty[String, IRI]

	private def stabilize[T >: IRI](v: T, vf: ValueFactory): T = v match
		case bn: BNode => synchronized:
			nodeLookup.getOrElseUpdate(bn.getID, {
				i += 1
				vf.createIRI(graph, s"bnode_$i")
			})
		case other => other

	/**
	  * "Stabilizes" blank nodes, that may be present as subject of object of the statement,
	  * by converting them to IRIs. The IRIs have graph IRI as prefix, and the last segment
	  * of the form "bnode_$i" where i is the order of first occurence of the blank node to
	  * this routine
	  *
	  * @param st
	  * @param vf
	  * @return
	  */
	def stabilizeBnodes(st: Statement, vf: ValueFactory): Statement =
		val subj = st.getSubject
		val obj = st.getObject
		if subj.isBNode || obj.isBNode then
			vf.createStatement(stabilize(subj, vf), st.getPredicate, stabilize(obj, vf), st.getContext)
		else st
