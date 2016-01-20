package se.lu.nateko.cp.meta.services

import org.openrdf.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

class CpmetaVocab (val factory: ValueFactory) extends CustomVocab { top =>

	val baseUri = "http://meta.icos-cp.eu/ontologies/cpmeta/"

	val atmoStationClass = getRelative("AS")
	val ecoStationClass = getRelative("ES")
	val oceStationClass = getRelative("OS")

	val productionClass = getRelative("DataProduction")
	val submissionClass = getRelative("DataSubmission")
	val dataPackageClass = getRelative("DataPackage")

	val hasLatitude = getRelative("hasLatitude")
	val hasLongitude = getRelative("hasLongitude")
	val hasName = getRelative("hasName")
	val hasStationId = getRelative("hasStationId")

	val hasSha256sum = getRelative("hasSha256sum")
	val wasSubmittedBy = getRelative("wasSubmittedBy")
	val wasProducedBy = getRelative("wasProducedBy")
	val hasDataLevel = getRelative("hasDataLevel")
	val hasPackageSpec = getRelative("hasPackageSpec")

	object prov extends CustomVocab {
		val factory = top.factory
		val baseUri = "http://www.w3.org/ns/prov#"
		val wasAssociatedWith = getRelative("wasAssociatedWith")
		val startedAtTime = getRelative("startedAtTime")
		val endedAtTime = getRelative("endedAtTime")
	}

	def getFile(hash: Sha256Sum) = factory.createURI("https://data.icos-cp.eu/files/", hash.base64Url)
	def getProduction(hash: Sha256Sum) = getRelative("prod_" + hash.base64Url)
	def getSubmission(hash: Sha256Sum) = getRelative("subm_" + hash.base64Url)
}
