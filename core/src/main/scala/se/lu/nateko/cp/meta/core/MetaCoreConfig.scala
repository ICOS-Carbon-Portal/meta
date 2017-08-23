package se.lu.nateko.cp.meta.core

import java.net.URI

case class MetaCoreConfig(
	dataObjPrefix: URI,
	landingPagePrefix: URI,
	metaResourcePrefix: URI,
	handleService: URI
)

object MetaCoreConfig{
	import CommonJsonSupport._

	implicit val metaCoreConfigFormat = jsonFormat4(MetaCoreConfig.apply)
}
