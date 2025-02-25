package se.lu.nateko.cp.meta.services.upload

import se.lu.nateko.cp.doi.core.{DoiClient, DoiClientConfig, DoiReadonlyClient, PlainJavaDoiHttp}

import eu.icoscp.envri.Envri
import scala.concurrent.ExecutionContext
import se.lu.nateko.cp.meta.DoiConfig

class DoiClientFactory(conf: DoiConfig)(using ExecutionContext):

	def getClient(using envri: Envri): DoiClient =
		val memberConf = conf.envries(envri)
		val http = new PlainJavaDoiHttp(Some(memberConf.symbol), Some(memberConf.password))
		val doiConf = DoiClientConfig(conf.restEndpoint, memberConf)
		new DoiClient(doiConf, http)

	object client extends DoiReadonlyClient(conf, PlainJavaDoiHttp(None, None))
