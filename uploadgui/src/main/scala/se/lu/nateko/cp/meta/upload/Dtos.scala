package se.lu.nateko.cp.meta.upload

import java.net.URI

import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig

case class Station(namedUri: NamedUri, id: String)

case class DsSpec(uri: URI, dsClass: DsSpec.DsClass)
object DsSpec{
	type DsClass = String
	val SpatioTemp: DsClass = "Spatiotemporal"
	val StationTimeSer: DsClass = "StationTimeSeries"
}

case class ObjSpec(uri: URI, name: String, dataLevel: Int, dataset: Option[DsSpec], theme: URI, project: URI, keywords: Seq[String]){
	def isStationTimeSer: Boolean = dataset.exists(_.dsClass == DsSpec.StationTimeSer)
	def isSpatiotemporal: Boolean = dataset.exists(_.dsClass == DsSpec.SpatioTemp)
}

case class InitAppInfo(userEmail: Option[String], envri: Envri, envriConfig: EnvriConfig)

case class NamedUri(uri: URI, name: String)

case class SamplingPoint(uri: URI, latitude: Double, longitude: Double, name: String)

class SpatialCoverage(val uri: URI, val label: String)

case class DatasetVar(label: String, title: String, valueType: String, unit: String, isOptional: Boolean, isRegex: Boolean)
