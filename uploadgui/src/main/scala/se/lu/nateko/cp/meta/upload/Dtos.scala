package se.lu.nateko.cp.meta.upload

import java.net.URI

import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig

case class Station(uri: URI, id: String, name: String, orgClass: String)

case class ObjSpec(uri: URI, name: String, dataLevel: Int)

case class InitAppInfo(userEmail: Option[String], envri: Envri, envriConfig: EnvriConfig)
