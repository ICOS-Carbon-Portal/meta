package se.lu.nateko.cp.meta.services.upload

import se.lu.nateko.cp.doi.core.DoiClient
import se.lu.nateko.cp.doi.core.DoiClientConfig
import se.lu.nateko.cp.doi.core.PlainJavaDoiHttp
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.core.data.Envri

import scala.concurrent.ExecutionContext

class DoiClientFactory(doiConfs: Map[Envri, DoiClientConfig])(using ExecutionContext):

	def getClient(using envri: Envri): DoiClient = {
		val doiConf = doiConfs(envri)
		val http = new PlainJavaDoiHttp(doiConf.symbol, doiConf.password)
		new DoiClient(doiConf, http)
	}
