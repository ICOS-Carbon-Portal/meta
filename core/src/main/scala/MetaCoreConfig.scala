package se.lu.nateko.cp.meta.core

import scala.language.unsafeNulls

import com.typesafe.config.ConfigFactory
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.cpauth.core.ConfigLoader.parseAs
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import spray.json.DefaultJsonProtocol.*
import spray.json.RootJsonFormat

import java.net.URI



case class MetaCoreConfig(
	handleProxies: HandleProxiesConfig,
	envriConfigs: Map[Envri, EnvriConfig]
)

case class HandleProxiesConfig(basic: URI, doi: URI)

object MetaCoreConfig extends CommonJsonSupport{

	given RootJsonFormat[HandleProxiesConfig] = jsonFormat2(HandleProxiesConfig.apply)
	given RootJsonFormat[Envri] = enumFormat(Envri.valueOf, Envri.values)
	given RootJsonFormat[EnvriConfig] = jsonFormat6(EnvriConfig.apply)
	given RootJsonFormat[MetaCoreConfig] = jsonFormat2(MetaCoreConfig.apply)

	def default = ConfigFactory.defaultReference().getValue("metacore").parseAs[MetaCoreConfig]
}
