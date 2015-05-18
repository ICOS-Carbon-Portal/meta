package se.lu.nateko.cp.meta

import spray.json.DefaultJsonProtocol
import spray.httpx.SprayJsonSupport

case class ResourceInfo(displayName: String, uri: String)

object CpmetaJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport{

	implicit val resourceInfoFormat = jsonFormat2(ResourceInfo)

}
