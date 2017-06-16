package se.lu.nateko.cp.meta.ingestion.badm

import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab

class BadmVocab (val factory: ValueFactory) extends CustomVocab {

	val baseUri = "http://meta.icos-cp.eu/ontologies/badm/"

	def getDataProp(variable: String) = getRelative("dprop_" + variable)
	def getObjProp(variable: String) = getRelative("oprop_" + variable)
	def getVocabValue(variable: String, value: String) = getRelative(s"val_${variable}_${value}")

}
