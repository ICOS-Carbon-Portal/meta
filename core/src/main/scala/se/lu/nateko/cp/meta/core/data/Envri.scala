package se.lu.nateko.cp.meta.core.data

import java.net.URI
import se.lu.nateko.cp.meta.core.MetaCoreConfig.EnvriConfigs

object Envri extends Enumeration{

	type Envri = Value

	val ICOS, SITES = Value

	def infer(uri: URI)(implicit configs: EnvriConfigs): Option[Envri] = infer(uri.getHost)

	def infer(hostname: String)(implicit configs: EnvriConfigs): Option[Envri] = {

		def matches(uri: URI) = hostname == uri.getHost

		configs.collectFirst{
			case (envri, conf) if matches(conf.metaPrefix) || matches(conf.dataPrefix) => envri
		}
	}
}
