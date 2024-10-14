package se.lu.nateko.cp.meta.upload

import java.net.URI

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.core.data.DatasetType
import se.lu.nateko.cp.meta.OntoConstants.FormatUris.*

case class Station(namedUri: NamedUri, id: String)

case class ObjSpec(
	uri: URI,
	name: String,
	dataLevel: Int,
	dataset: Option[URI],
	specificDatasetType: DatasetType,
	theme: URI,
	project: URI,
	format: URI,
	keywords: Seq[String]
){
	def isSpatiotemporal = specificDatasetType == DatasetType.SpatioTemporal
	def isStationTimeSer = specificDatasetType == DatasetType.StationTimeSeries
	def isSitesProjectData = project == URI("https://meta.fieldsites.se/resources/projects/community-projects")
	val isZip = format == zipArchive || format == excel
	val isNetCDF = format == netCdf || format == netCdfTimeSeries
	val isNonIngestableNetCDF = isNetCDF && dataset.isEmpty
}

case class InitAppInfo(userEmail: Option[String], envri: Envri, envriConfig: EnvriConfig)

case class NamedUri(uri: URI, name: String)

case class SamplingPoint(uri: URI, latitude: Double, longitude: Double, name: String)

class SpatialCoverage(val uri: URI, val label: String)

case class DatasetVar(label: String, title: String, valueType: String, unit: String, isOptional: Boolean, isRegex: Boolean)
