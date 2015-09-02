package se.lu.nateko.cp.meta.ingestion

import java.net.URI



case class BasicStation(
	val stationTheme: String,
	val shortName: String,
	val longName: String,
	val stationClass: Int,
	val country: String,
	val elevationAG: String,
	val elevationAS: Float,
	val fundingForConstruction: String,
	val fundingForOperation: String,
	val siteType: String,
	val operationalDateEstimate: String,
	val stationKind: String,
	val isOperational: Boolean,
	val piName: String,
	val piEmail: String
)

sealed trait Station
case class OceanStation(val basic: BasicStation, val location: Either[String, (Double, Double)]) extends Station
case class AtmEcoStation(val basic: BasicStation, val lat: Double, val lon: Double) extends Station

object StationsIngestion {

	def getStationsTable: TextTable = {
		val stationsStream = getClass.getResourceAsStream("/stations.csv")
		new TsvDataTable(stationsStream)
	}

	def getUri(stationType: String): URI = {
		themeToUri(stationType)
	}

	val prefix = "http://meta.icos-cp.eu/ontologies/stationentry/"

	val themeToUri = Map(
		"eco" -> new URI(prefix + "ES"),
		"atm" -> new URI(prefix + "AS"),
		"ocean" -> new URI(prefix + "OS"),
		"oce" -> new URI(prefix + "OS")
	)
}

