package se.lu.nateko.cp.meta.core.data

import java.net.URI
import eu.icoscp.envri.Envri

//TODO Add logotype URL
final case class EnvriConfig(
	authHost: String,
	dataHost: String,
	metaHost: String,
	dataItemPrefix: URI,
	metaItemPrefix: URI,
	defaultTimezoneId: String
){
	def matchesHost(host: String): Boolean =
		host == dataHost || host == metaHost ||
		host == dataItemPrefix.getHost || host == metaItemPrefix.getHost
}

type EnvriConfigs = Map[Envri, EnvriConfig]

object EnvriResolver{

	def infer(uri: URI)(using EnvriConfigs): Option[Envri] = infer(uri.getHost)

	def infer(hostname: String)(using configs: EnvriConfigs): Option[Envri] = configs.collectFirst{
		case (envri, conf) if conf.matchesHost(hostname) => envri
	}

}
