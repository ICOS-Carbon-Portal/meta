package se.lu.nateko.cp.meta.services.upload

import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.doi.core.DoiClient
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.doi.core.PlainJavaDoiHttp
import se.lu.nateko.cp.doi.core.DoiClientConfig
import scala.concurrent.ExecutionContext

class DoiServiceClient(conf: CpmetaConfig)(using ExecutionContext) {
  	def getClient(using envri: Envri): DoiClient = {
		val doiConf = conf.doi(envri)
		val http = new PlainJavaDoiHttp(doiConf.symbol, doiConf.password)
		val clientConf = new DoiClientConfig(doiConf.symbol, doiConf.password, doiConf.restEndpoint.toURL(), doiConf.prefix)
		new DoiClient(clientConf, http)
	}

}
