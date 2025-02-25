package se.lu.nateko.cp.meta

import java.net.URI

object OntoConstants:
	val CpmetaPrefix = "http://meta.icos-cp.eu/ontologies/cpmeta/"

	def getOntologyUrl(suffix: String): URI = URI(s"$CpmetaPrefix$suffix")

	val zipFormatSuff = "zipArchive"
	val excelFormatSuff = "excel"
	val netCdfFormatSuff = "netcdf"
	val netCdfTsFormatSuff = "netcdfTimeSeries"

	object FormatUris:
		val zipArchive: URI = getOntologyUrl(zipFormatSuff)
		val excel: URI = getOntologyUrl(excelFormatSuff)
		val netCdf: URI = getOntologyUrl(netCdfFormatSuff)
		val netCdfTimeSeries: URI = getOntologyUrl(netCdfTsFormatSuff)

end OntoConstants
