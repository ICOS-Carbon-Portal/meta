package se.lu.nateko.cp.meta.ingestion

import org.openrdf.model.ValueFactory
import org.openrdf.model.URI

class Vocab(factory: ValueFactory) {

	private val baseUri = "http://meta.icos-cp.eu/ontologies/cpmeta/"

	val base: URI = factory.createURI(baseUri)

	def getRelative(local: String): URI = factory.createURI(baseUri, local)
	def getRelative(baseUri: URI, local: String): URI = factory.createURI(baseUri.stringValue, local)

	val station = getRelative("Station/")
	val atmoStation  = getRelative(station, "AS")
	val ecoStation  = getRelative(station, "ES")

	val hasLatitude = getRelative("hasLatitude")
	val hasLongitude = getRelative("hasLongitude")
	val hasName = getRelative("hasName")
}