package se.lu.nateko.cp.meta.core.data

import java.net.URI
import se.lu.nateko.cp.meta.core.MetaCoreConfig.EnvriConfigs

object Envri extends Enumeration{

	type Envri = Value

	val ICOS, SITES = Value

	def infer(metaEntity: URI)(implicit configs: EnvriConfigs): Option[Envri] = infer(metaEntity.getHost)

	def infer(hostname: String)(implicit configs: EnvriConfigs): Option[Envri] =
		configs.collectFirst{
			case (envri, conf) if hostname == conf.metaPrefix.getHost => envri
		}
}
