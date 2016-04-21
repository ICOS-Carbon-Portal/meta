package se.lu.nateko.cp.meta.ingestion

import org.openrdf.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab

class CpVocab (val factory: ValueFactory) extends CustomVocab {

	val baseUri = "http://meta.icos-cp.eu/resources/"

	def getAtmosphericStation(siteId: String) = getRelative("stations/as/", siteId)
	def getEcosystemStation(siteId: String) = getRelative("stations/es/", siteId)
	def getOceanStation(siteId: String) = getRelative("stations/os/", siteId)

	def getAncillaryEntry(valueId: String) = getRelative("ancillary/", valueId)
}
