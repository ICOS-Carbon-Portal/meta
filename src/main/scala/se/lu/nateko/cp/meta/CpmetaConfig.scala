package se.lu.nateko.cp.meta

import java.net.URI
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import spray.json._
import se.lu.nateko.cp.meta.persistence.postgres.DbServer
import se.lu.nateko.cp.meta.persistence.postgres.DbCredentials
import se.lu.nateko.cp.cpauth.core.PublicAuthConfig

case class RdflogConfig(server: DbServer, credentials: DbCredentials)

case class IngestionConfig(
	ingesterId: String,
	ingestAtStartup: Option[Boolean],
	ingestionInterval: Option[Int] //seconds
)

case class InstanceServerConfig(
	logName: Option[String],
	readContexts: Option[Seq[URI]],
	writeContexts: Seq[URI],
	ingestion: Option[IngestionConfig]
)

case class OntoConfig(
	owlResource: String,
	instanceServerId: String
)

case class DataSubmitterConfig(
	authorizedUserIds: Seq[String],
	datasetClass: URI,
	dataStructureClass: URI
)

case class UploadServiceConfig(submitters: Map[URI, DataSubmitterConfig])

case class CpmetaConfig(
	port: Int,
	dataUploadService: UploadServiceConfig,
	instanceServers: Map[String, InstanceServerConfig],
	rdfLog: RdflogConfig,
	onto: OntoConfig,
	auth: PublicAuthConfig
)

object ConfigLoader extends CpmetaJsonProtocol{

	implicit val ingestionConfigFormat = jsonFormat3(IngestionConfig)
	implicit val instanceServerConfigFormat = jsonFormat4(InstanceServerConfig)
	implicit val dbServerFormat = jsonFormat2(DbServer)
	implicit val dbCredentialsFormat = jsonFormat3(DbCredentials)
	implicit val rdflogConfigFormat = jsonFormat2(RdflogConfig)
	implicit val publicAuthConfigFormat = jsonFormat2(PublicAuthConfig)
	implicit val ontoConfigFormat = jsonFormat2(OntoConfig)
	implicit val dataSubmitterConfigFormat = jsonFormat3(DataSubmitterConfig)
	implicit val uploadServiceConfigFormat = jsonFormat1(UploadServiceConfig)
	implicit val cpmetaConfigFormat = jsonFormat6(CpmetaConfig)

	def getAppConfig: Config = {
		val confFile = new java.io.File("application.conf").getAbsoluteFile
		val default = ConfigFactory.load
		if(!confFile.exists) default
		ConfigFactory.parseFile(confFile).withFallback(default)
	}

	def getDefault: CpmetaConfig = {
		val renderOpts = ConfigRenderOptions.concise.setJson(true)
		val confJson: String = getAppConfig.getValue("cpmeta").render(renderOpts)
		
		confJson.parseJson.convertTo[CpmetaConfig]
	}

}
