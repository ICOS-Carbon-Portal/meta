package se.lu.nateko.cp.meta.services

import org.openrdf.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.{URI => JavaUri}
import java.net.URLEncoder

class CpmetaVocab (val factory: ValueFactory) extends CustomVocab { top =>

	val baseUri = "http://meta.icos-cp.eu/ontologies/cpmeta/"

	val atmoStationClass = getRelative("AS")
	val ecoStationClass = getRelative("ES")
	val oceStationClass = getRelative("OS")

	val productionClass = getRelative("DataProduction")
	val submissionClass = getRelative("DataSubmission")
	val dataObjectClass = getRelative("DataObject")

	val hasLatitude = getRelative("hasLatitude")
	val hasLongitude = getRelative("hasLongitude")
	val hasName = getRelative("hasName")
	val hasPID = getRelative("hasPid")
	val hasStationId = getRelative("hasStationId")

	val hasSha256sum = getRelative("hasSha256sum")
	val wasSubmittedBy = getRelative("wasSubmittedBy")
	val wasProducedBy = getRelative("wasProducedBy")
	val hasDataLevel = getRelative("hasDataLevel")
	val hasPackageSpec = getRelative("hasObjectSpec")
	val hasFormat = getRelative("hasFormat")
	val hasEncoding = getRelative("hasEncoding")

	object prov extends CustomVocab {
		val factory = top.factory
		val baseUri = "http://www.w3.org/ns/prov#"
		val wasAssociatedWith = getRelative("wasAssociatedWith")
		val startedAtTime = getRelative("startedAtTime")
		val endedAtTime = getRelative("endedAtTime")
	}

	def getDataObject(hash: Sha256Sum) = factory.createURI("https://meta.icos-cp.eu/objects/", hash.id)
	def getDataObjectAccessUrl(hash: Sha256Sum, fileName: Option[String]): JavaUri = {
		val filePath = fileName.map("/" + URLEncoder.encode(_, "UTF-8")).getOrElse("")
		new JavaUri(s"https://data.icos-cp.eu/objects/${hash.id}$filePath")
	}

	def getProduction(hash: Sha256Sum) = getRelative("prod_" + hash.id)
	def getSubmission(hash: Sha256Sum) = getRelative("subm_" + hash.id)
}
