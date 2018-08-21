package se.lu.nateko.cp.meta.upload

import java.net.URI

case class Station(uri: URI, id: String, name: String, orgClass: String)

case class ObjSpec(uri: URI, name: String, dataLevel: Int)
