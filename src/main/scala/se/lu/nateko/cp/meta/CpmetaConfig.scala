package se.lu.nateko.cp.meta

import java.net.URI
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import spray.json._
import se.lu.nateko.cp.meta.persistence.postgres.DbServer
import se.lu.nateko.cp.meta.persistence.postgres.DbCredentials
import se.lu.nateko.cp.cpauth.core.PublicAuthConfig
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.Envri

case class RdflogConfig(server: DbServer, credentials: DbCredentials)

case class IngestionConfig(
	ingesterId: String,
	waitFor: Option[Seq[String]],
	ingestAtStartup: Option[Boolean]
)

case class InstanceServerConfig(
	writeContexts: Seq[URI],
	logName: Option[String],
	skipLogIngestionAtStart: Option[Boolean],
	readContexts: Option[Seq[URI]],
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
	forDataObjects: Map[Envri, DataObjectInstServersConfig],
	cpMetaInstanceServerId: String,
	icosMetaInstanceServerId: String,
	otcMetaInstanceServerId: String
)

case class SchemaOntologyConfig(ontoId: Option[String], owlResource: String)

case class InstOntoServerConfig(
	serviceTitle: String,
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
	submittingOrganization: URI,
	authorizedThemes: Option[Seq[URI]],
	authorizedProjects: Option[Seq[URI]]
)

case class SubmittersConfig(submitters: Map[Envri, Map[String, DataSubmitterConfig]])

case class EtcConfig(
	eddyCovarObjSpecId: String,
	storageObjSpecId: String,
	bioMeteoObjSpecId: String,
	saheatObjSpecId: String,
	metaService: URI,
	ingestFileMetaAtStart: Boolean
)

case class UploadServiceConfig(
	metaServers: Map[Envri, String],
	collectionServers: Map[Envri, String],
	documentServers: Map[Envri, String],
	epicPid: EpicPidConfig,
	handle: HandleNetClientConfig,
	etc: EtcConfig
)

case class EmailConfig(
	mailSendingActive: Boolean,
	smtpServer: String,
	username: String,
	password: String,
	fromAddress: String,
	logBccAddress: Option[String]
)

case class LabelingServiceConfig(
	instanceServerId: String,
	provisionalInfoInstanceServerId: String,
	tcUserIds: Map[URI, Seq[String]],
	dgUserId: String,
	riComEmail: String,
	calLabEmails: Seq[String],
	mailing: EmailConfig,
	ontoId: String
)

case class EpicPidConfig(
	url: String,
	prefix: String,
	password: String,
	dryRun: Boolean
)

case class HandleNetClientConfig(
	prefix: Map[Envri, String],
	baseUrl: String,
	serverCertPemFilePath: Option[String],
	clientCertPemFilePath: String,
	clientPrivKeyPKCS8FilePath: String,
	dryRun: Boolean
)

case class SparqlServerConfig(
	maxQueryRuntimeSec: Int,
	quotaPerMinute: Int,//in seconds
	quotaPerHour: Int,  //in seconds
	maxParallelQueries: Int,
	maxQueryQueue: Int,
	banLength: Int, //in minutes
	maxCacheableQuerySize: Int, //in bytes
	adminUsers: Seq[String]
)

case class RdfStorageConfig(path: String, recreateAtStartup: Boolean, indices: String, disableCpIndex: Boolean)

case class CitationConfig(style: String, eagerWarmUp: Boolean, timeoutSec: Int)

case class RestheartConfig(baseUri: String, dbNames: Map[Envri, String]) {
	def dbName(implicit envri: Envri): String = dbNames(envri)
}

case class StatsClientConfig(downloadsUri: String, previews: RestheartConfig)

case class DoiClientConfig(
	symbol: String,
	password: String,
	restEndpoint: URI,
	prefix: String
)

case class CpmetaConfig(
	port: Int,
	dataUploadService: UploadServiceConfig,
	stationLabelingService: LabelingServiceConfig,
	instanceServers: InstanceServersConfig,
	rdfLog: RdflogConfig,
	fileStoragePath: String,
	rdfStorage: RdfStorageConfig,
	onto: OntoConfig,
	auth: Map[Envri, PublicAuthConfig],
	core: MetaCoreConfig,
	sparql: SparqlServerConfig,
	citations: CitationConfig,
	statsClient: StatsClientConfig,
	doi: Map[Envri, DoiClientConfig]
)

object ConfigLoader extends CpmetaJsonProtocol{

	import MetaCoreConfig.envriFormat

	implicit val ingestionConfigFormat = jsonFormat3(IngestionConfig)
	implicit val instanceServerConfigFormat = jsonFormat5(InstanceServerConfig)
	implicit val dataObjectInstServerDefinitionFormat = jsonFormat2(DataObjectInstServerDefinition)
	implicit val dataObjectInstServersConfigFormat = jsonFormat3(DataObjectInstServersConfig)
	implicit val instanceServersConfigFormat = jsonFormat5(InstanceServersConfig)
	implicit val dbServerFormat = jsonFormat2(DbServer)
	implicit val dbCredentialsFormat = jsonFormat3(DbCredentials)
	implicit val rdflogConfigFormat = jsonFormat2(RdflogConfig)
	implicit val publicAuthConfigFormat = jsonFormat4(PublicAuthConfig)
	implicit val schemaOntologyConfigFormat = jsonFormat2(SchemaOntologyConfig)
	implicit val instOntoServerConfigFormat = jsonFormat4(InstOntoServerConfig)
	implicit val ontoConfigFormat = jsonFormat2(OntoConfig)
	implicit val dataSubmitterConfigFormat = jsonFormat6(DataSubmitterConfig)
	implicit val submittersConfigFormat = jsonFormat1(SubmittersConfig)
	implicit val epicPidFormat = jsonFormat4(EpicPidConfig)
	implicit val etcUploadConfigFormat = jsonFormat6(EtcConfig)
	implicit val handleClientFormat = jsonFormat6(HandleNetClientConfig)

	implicit val uploadServiceConfigFormat = jsonFormat6(UploadServiceConfig)
	implicit val emailConfigFormat = jsonFormat6(EmailConfig)
	implicit val labelingServiceConfigFormat = jsonFormat8(LabelingServiceConfig)
	implicit val sparqlConfigFormat = jsonFormat8(SparqlServerConfig)
	implicit val rdfStorageConfigFormat = jsonFormat4(RdfStorageConfig)
	implicit val citationConfigFormat = jsonFormat3(CitationConfig)
	implicit val restHeartConfigFormat = jsonFormat2(RestheartConfig)
	implicit val statsClientConfigFormat = jsonFormat2(StatsClientConfig)
	implicit val doiClientConfigFormat = jsonFormat4(DoiClientConfig)

	implicit val cpmetaConfigFormat = jsonFormat14(CpmetaConfig)

	val appConfig: Config = {
		val confFile = new java.io.File("application.conf").getAbsoluteFile
		val default = ConfigFactory.load
		if(confFile.exists)
			ConfigFactory.parseFile(confFile).withFallback(default)
		else default
	}

	private val renderOpts = ConfigRenderOptions.concise.setJson(true)

	val default: CpmetaConfig = {
		val confJson: String = appConfig.getValue("cpmeta").render(renderOpts)

		confJson.parseJson.convertTo[CpmetaConfig]
	}

	def submittersConfig: SubmittersConfig = {
		val confFile = new java.io.File("submitters.conf").getAbsoluteFile

		if(confFile.exists) {
			val confJson: String = ConfigFactory.parseFile(confFile).root.render(renderOpts)
			confJson.parseJson.convertTo[SubmittersConfig]
		} else {
			SubmittersConfig(Envri.values.iterator.map(_ -> Map.empty[String, DataSubmitterConfig]).toMap)
		}
	}

}
