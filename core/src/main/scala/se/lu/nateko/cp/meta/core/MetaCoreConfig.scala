package se.lu.nateko.cp.meta.core

import java.net.URI
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import spray.json.JsonFormat
import spray.json.DefaultJsonProtocol._


case class MetaCoreConfig(
	handleProxies: HandleProxiesConfig,
	envriConfigs: Map[Envri, EnvriConfig]
)

case class HandleProxiesConfig(basic: URI, doi: URI)

object MetaCoreConfig extends CommonJsonSupport{

	given JsonFormat[HandleProxiesConfig] = jsonFormat2(HandleProxiesConfig.apply)
	given JsonFormat[Envri] = enumFormat(Envri)
	given JsonFormat[EnvriConfig] = jsonFormat6(EnvriConfig.apply)
	given JsonFormat[MetaCoreConfig] = jsonFormat2(MetaCoreConfig.apply)
}
