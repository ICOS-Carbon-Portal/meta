package se.lu.nateko.cp.meta.core

import java.net.URI
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol.*


final case class MetaCoreConfig(
	handleProxies: HandleProxiesConfig,
	envriConfigs: Map[Envri, EnvriConfig]
)

final case class HandleProxiesConfig(basic: URI, doi: URI)

object MetaCoreConfig extends CommonJsonSupport{

	given RootJsonFormat[HandleProxiesConfig] = jsonFormat2(HandleProxiesConfig.apply)
	given RootJsonFormat[Envri] = enumFormat(Envri.valueOf, Envri.values)
	given RootJsonFormat[EnvriConfig] = jsonFormat6(EnvriConfig.apply)
	given RootJsonFormat[MetaCoreConfig] = jsonFormat2(MetaCoreConfig.apply)
}
