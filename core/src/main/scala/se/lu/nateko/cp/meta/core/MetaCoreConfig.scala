package se.lu.nateko.cp.meta.core

import java.net.URI
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig


case class MetaCoreConfig(
	handleProxies: HandleProxiesConfig,
	envriConfigs: Map[Envri, EnvriConfig]
)

case class HandleProxiesConfig(basic: URI, doi: URI)

object MetaCoreConfig{
	import CommonJsonSupport._

	implicit val handleProxiesConfigFormat = jsonFormat2(HandleProxiesConfig)
	implicit val envriFormat = enumFormat(Envri)
	implicit val envriConfigFormat = jsonFormat6(EnvriConfig)
	implicit val metaCoreConfigFormat = jsonFormat2(MetaCoreConfig.apply)
}
