package se.lu.nateko.cp.meta.upload

import java.net.URI

import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig

case class Station(uri: URI, id: String, name: String)

case class ObjSpec(uri: URI, name: String, dataLevel: Int, hasDataset: Boolean, theme: URI, project: URI, keywords: Seq[String])

case class InitAppInfo(userEmail: Option[String], envri: Envri, envriConfig: EnvriConfig)

case class Site(uri: URI, name: String)

case class SamplingPoint(uri: URI, latitude: Double, longitude: Double, name: String)

class SpatialCoverage(val uri: URI, val label: String)
