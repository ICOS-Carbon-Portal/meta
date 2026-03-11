package devtools.config

import com.typesafe.config.{ConfigFactory, Config}
import org.slf4j.LoggerFactory

def rdfStoragePath = {
	val path = readConfig().getValue("devtools.rdfStoragePath").nn.unwrapped.toString
	log.info(s"Using rdfStorage path: $path")
	path
}

private def readConfig(): Config = {
	val path = new java.io.File("application.conf").getAbsoluteFile
	ConfigFactory.parseFile(path).nn.resolve.nn
}

private val log = LoggerFactory.getLogger("devtools.Config").nn

