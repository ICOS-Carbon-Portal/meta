package tools.shared.config

import com.typesafe.config.{ConfigFactory, Config}
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.CpmetaConfig

def rdfStoragePath = {
	val path = readConfig().getValue("tools.rdfStoragePath").nn.unwrapped.toString
	log.info(s"Using rdfStorage path: $path")
	path
}

def cpmetaConfig: CpmetaConfig =
	import se.lu.nateko.cp.meta.ConfigLoader.given
	import se.lu.nateko.cp.cpauth.core.ConfigLoader.parseAs
	val mergedConf = readConfig().withFallback(ConfigFactory.load().nn).nn.resolve.nn
	mergedConf.getValue("cpmeta").nn.parseAs[CpmetaConfig]

private def readConfig(): Config = {
	val path = new java.io.File("application.conf").getAbsoluteFile
	ConfigFactory.parseFile(path).nn.resolve.nn
}

private val log = LoggerFactory.getLogger("tools.Config").nn

