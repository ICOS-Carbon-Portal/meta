package se.lu.nateko.cp.meta

import scala.util.Try
import com.typesafe.config.ConfigFactory
import se.lu.nateko.cp.meta.persistence.postgres.DbServer
import se.lu.nateko.cp.meta.persistence.postgres.DbCredentials
import com.typesafe.config.Config

trait AppConfig {
	def instOntUri: String
	def instOwlFileResourcePath: Option[String]
	def schemaOwlFileResourcePath: String

	def rdfLogDbServer: DbServer
	def rdfLogDbCredentials: DbCredentials
	def rdfLogName: String
}

object AppConfig{

	import ConfigExtensions._

	def load: Try[AppConfig] = Try{
		val confFile = new java.io.File("application.conf").getAbsoluteFile
		if(!confFile.exists) throw new Exception(s"Expected to find a config file at $confFile")

		val conf = ConfigFactory.parseFile(confFile).withFallback(ConfigFactory.load)

		new AppConfig{
			val instOntUri = conf.getString("instanceOntologyUri")
			val instOwlFileResourcePath = conf.getOpt[String]("instanceOWLFileResourcePath")
			val schemaOwlFileResourcePath = conf.getString("schemaOWLFileResourcePath")

			val logDbConf = conf.getConfig("rdfLogDb")

			val rdfLogName = logDbConf.getString("logname")
			val rdfLogDbServer = DbServer(
				host = logDbConf.getString("host"),
				port = logDbConf.getInt("port")
			)
			val rdfLogDbCredentials = DbCredentials(
				db = logDbConf.getString("db"),
				user = logDbConf.getString("user"),
				password = logDbConf.getString("password")
			)
		}
	}

}

object ConfigExtensions{

	implicit class ExtendedConfig(val conf: Config) extends AnyVal{

		def getOpt[T](path: String): Option[T] = {
			if(conf.hasPath(path)) Some(conf.getAnyRef(path).asInstanceOf[T])
			else None
		}

		def getOr[T](path: String, default: T): T = getOpt[T](path).getOrElse(default)

	}

}