package se.lu.nateko.cp.meta.core

import java.net.URI
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import spray.json.{JsonFormat, RootJsonFormat}
import spray.json.DefaultJsonProtocol._


case class MetaCoreConfig(
	handleProxies: HandleProxiesConfig,
	envriConfigs: Map[Envri, EnvriConfig]
)

case class HandleProxiesConfig(basic: URI, doi: URI)

object MetaCoreConfig extends CommonJsonSupport{

	given RootJsonFormat[HandleProxiesConfig] = jsonFormat2(HandleProxiesConfig.apply)
	given RootJsonFormat[Envri] = enumFormat(Envri)
	given RootJsonFormat[EnvriConfig] = jsonFormat6(EnvriConfig.apply)
	given RootJsonFormat[MetaCoreConfig] = jsonFormat2(MetaCoreConfig.apply)
}
