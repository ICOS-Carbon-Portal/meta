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
	waitFor: Option[Seq[String]],
	ingestAtStartup: Option[Boolean]
)

case class InstanceServerConfig(
	logName: Option[String],
	readContexts: Option[Seq[URI]],
	writeContexts: Seq[URI],
	ingestion: Option[IngestionConfig]
)

case class DataObjectInstServerDefinition(label: String, format: URI)

case class DataObjectInstServersConfig(
	commonReadContexts: Seq[URI],
	uriPrefix: URI,
	definitions: Seq[DataObjectInstServerDefinition]
)

case class InstanceServersConfig(
	specific: Map[String, InstanceServerConfig],
	forDataObjects: DataObjectInstServersConfig
)

case class SchemaOntologyConfig(ontoId: Option[String], owlResource: String)

case class InstOntoServerConfig(
	ontoId: String,
	instanceServerId: String,
	authorizedUserIds: Seq[String]
)

case class OntoConfig(
	ontologies: Seq[SchemaOntologyConfig],
	instOntoServers: Map[String, InstOntoServerConfig]
)

case class DataSubmitterConfig(
	authorizedUserIds: Seq[String],
	producingOrganizationClass: Option[URI],
	producingOrganization: Option[URI],
	submittingOrganization: URI
)

case class UploadServiceConfig(
	icosMetaServerId: String,
	submitters: Map[String, DataSubmitterConfig],
	epicPid: EpicPidConfig
)

case class MailTemplatesConfig(submitted: String)
case class EmailConfig(
	mailSendingActive: Boolean,
	smtpServer: String,
	fromAddress: String,
	templatePaths: MailTemplatesConfig,
	logBccAddress: Option[String]
)

case class LabelingServiceConfig(
	instanceServerId: String,
	provisionalInfoInstanceServerId: String,
	tcUserIds: Map[URI, Seq[String]],
	mailing: EmailConfig,
	ontoId: String
)

case class EpicPidConfig(url: String, prefix: String, password: String)

case class CpmetaConfig(
	port: Int,
	dataUploadService: UploadServiceConfig,
	stationLabelingService: LabelingServiceConfig,
	instanceServers: InstanceServersConfig,
	rdfLog: RdflogConfig,
	fileStoragePath: String,
	onto: OntoConfig,
	auth: PublicAuthConfig
)

object ConfigLoader extends CpmetaJsonProtocol{

	implicit val ingestionConfigFormat = jsonFormat3(IngestionConfig)
	implicit val instanceServerConfigFormat = jsonFormat4(InstanceServerConfig)
	implicit val dataObjectInstServerDefinitionFormat = jsonFormat2(DataObjectInstServerDefinition)
	implicit val dataObjectInstServersConfigFormat = jsonFormat3(DataObjectInstServersConfig)
	implicit val instanceServersConfigFormat = jsonFormat2(InstanceServersConfig)
	implicit val dbServerFormat = jsonFormat2(DbServer)
	implicit val dbCredentialsFormat = jsonFormat3(DbCredentials)
	implicit val rdflogConfigFormat = jsonFormat2(RdflogConfig)
	implicit val publicAuthConfigFormat = jsonFormat2(PublicAuthConfig)
	implicit val schemaOntologyConfigFormat = jsonFormat2(SchemaOntologyConfig)
	implicit val instOntoServerConfigFormat = jsonFormat3(InstOntoServerConfig)
	implicit val ontoConfigFormat = jsonFormat2(OntoConfig)
	implicit val dataSubmitterConfigFormat = jsonFormat4(DataSubmitterConfig)
	implicit val epicPidFormat = jsonFormat3(EpicPidConfig)
	implicit val uploadServiceConfigFormat = jsonFormat3(UploadServiceConfig)
	implicit val templatesConfigFormat = jsonFormat1(MailTemplatesConfig)
	implicit val emailConfigFormat = jsonFormat5(EmailConfig)
	implicit val labelingServiceConfigFormat = jsonFormat5(LabelingServiceConfig)

	implicit val cpmetaConfigFormat = jsonFormat8(CpmetaConfig)

	private def getAppConfig: Config = {
		val confFile = new java.io.File("application.conf").getAbsoluteFile
		val default = ConfigFactory.load
		if(confFile.exists)
			ConfigFactory.parseFile(confFile).withFallback(default)
		else default
	}

	val default: CpmetaConfig = {
		val renderOpts = ConfigRenderOptions.concise.setJson(true)
		val confJson: String = getAppConfig.getValue("cpmeta").render(renderOpts)
		
		confJson.parseJson.convertTo[CpmetaConfig]
	}

}
