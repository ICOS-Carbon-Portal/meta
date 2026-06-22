package tools.shared.config

import com.typesafe.config.{ConfigFactory, Config}
import org.slf4j.LoggerFactory

def rdfStoragePath = {
	val path = readConfig().getValue("tools.rdfStoragePath").nn.unwrapped.toString
	log.info(s"Using rdfStorage path: $path")
	path
}

case class VirtuosoConfig(host: String, username: String, password: String)

def virtuosoConfig = {
	val conf = readConfig().getConfig("tools.virtuoso").nn
	val vc = VirtuosoConfig(
		host = conf.getString("host").nn,
		username = conf.getString("username").nn,
		password = conf.getString("password").nn
	)
	log.info(s"Using Virtuoso host: ${vc.host}")
	vc
}

private def readConfig(): Config = {
	val path = new java.io.File("application.conf").getAbsoluteFile
	ConfigFactory.parseFile(path).nn.resolve.nn
}

private val log = LoggerFactory.getLogger("tools.Config").nn

