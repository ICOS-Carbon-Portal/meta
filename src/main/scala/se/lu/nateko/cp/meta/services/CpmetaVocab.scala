package se.lu.nateko.cp.meta.services

import org.openrdf.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab

class CpmetaVocab (val factory: ValueFactory) extends CustomVocab { top =>

	val baseUri = "http://meta.icos-cp.eu/ontologies/cpmeta/"

	val atmoStationClass = getRelative("AS")
	val ecoStationClass = getRelative("ES")
	val oceStationClass = getRelative("OS")

	val acquisitionClass = getRelative("DataAcquisition")
	val submissionClass = getRelative("DataSubmission")

	val hasLatitude = getRelative("hasLatitude")
	val hasLongitude = getRelative("hasLongitude")
	val hasName = getRelative("hasName")
	val hasStationId = getRelative("hasStationId")

	val hasSha256sum = getRelative("hasSha256sum")
	val wasSubmittedBy = getRelative("wasSubmittedBy")
	val wasAcquiredBy = getRelative("wasAcquiredBy")

	object qb extends CustomVocab {
		val factory = CpmetaVocab.this.factory
		val baseUri = "http://purl.org/linked-data/cube#"
		val structure = getRelative("structure")
	}

	object prov extends CustomVocab {
		val factory = CpmetaVocab.this.factory
		val baseUri = "http://www.w3.org/ns/prov#"
		val wasAssociatedWith = getRelative("wasAssociatedWith")
		val startedAtTime = getRelative("startedAtTime")
		val endedAtTime = getRelative("endedAtTime")
	}
}
