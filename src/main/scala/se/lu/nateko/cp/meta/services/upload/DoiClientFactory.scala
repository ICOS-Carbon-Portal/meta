package se.lu.nateko.cp.meta.services.upload

import se.lu.nateko.cp.doi.core.DoiClient
import se.lu.nateko.cp.doi.core.DoiClientConfig
import se.lu.nateko.cp.doi.core.DoiReadonlyClient
import se.lu.nateko.cp.doi.core.PlainJavaDoiHttp
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.DoiConfig

import scala.concurrent.ExecutionContext
import eu.icoscp.envri.Envri

class DoiClientFactory(conf: DoiConfig)(using ExecutionContext):

	def getClient(using envri: Envri): DoiClient =
		val memberConf = conf.envries(envri)
		val http = new PlainJavaDoiHttp(Some(memberConf.symbol), Some(memberConf.password))
		val doiConf = DoiClientConfig(conf.restEndpoint, memberConf)
		new DoiClient(doiConf, http)

	object client extends DoiReadonlyClient(conf, PlainJavaDoiHttp(None, None))
