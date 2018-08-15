package se.lu.nateko.cp.meta.core

import java.net.URI
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig


case class MetaCoreConfig(
	handleService: URI,
	envriConfigs: Map[Envri, EnvriConfig]
)

object MetaCoreConfig{
	import CommonJsonSupport._

	implicit val envriFormat = enumFormat(Envri)
	implicit val envriConfigFormat = jsonFormat3(EnvriConfig)
	implicit val metaCoreConfigFormat = jsonFormat2(MetaCoreConfig.apply)
}
