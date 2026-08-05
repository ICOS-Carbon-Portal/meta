package se.lu.nateko.cp.meta

import java.net.URI

object OntoConstants:
	val CpmetaPrefix = "http://meta.icos-cp.eu/ontologies/cpmeta/"

	def getOntologyUrl(suffix: String) = URI(s"$CpmetaPrefix$suffix")

	val zipFormatSuff = "zipArchive"
	val excelFormatSuff = "excel"
	val netCdfFormatSuff = "netcdf"
	val netCdfTsFormatSuff = "netcdfTimeSeries"

	object FormatUris:
		val zipArchive = getOntologyUrl(zipFormatSuff)
		val excel = getOntologyUrl(excelFormatSuff)
		val netCdf = getOntologyUrl(netCdfFormatSuff)
		val netCdfTimeSeries = getOntologyUrl(netCdfTsFormatSuff)

end OntoConstants
