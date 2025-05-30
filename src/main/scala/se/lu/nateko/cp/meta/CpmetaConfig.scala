package se.lu.nateko.cp.meta
import com.typesafe.config.ConfigFactory
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.cpauth.core.ConfigLoader.{appConfig, parseAs}
import se.lu.nateko.cp.cpauth.core.{EmailConfig, PublicAuthConfig}
import se.lu.nateko.cp.doi.core.{DoiEndpointConfig, DoiMemberConfig}
import se.lu.nateko.cp.meta.core.CommonJsonSupport.TypeField
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.core.{MetaCoreConfig, toTypedJson}
import se.lu.nateko.cp.meta.persistence.postgres.{DbCredentials, DbServer}
import spray.json.*

import java.net.URI
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import scala.collection.mutable.WeakHashMap

case class RdflogConfig(server: DbServer, credentials: DbCredentials)

enum IngestionMode:
	case EAGER, BACKGROUND, OFF

case class IngestionConfig(
	ingesterId: String,
	waitFor: Option[Seq[String]],
	mode: IngestionMode
)

case class InstanceServerConfig(
	writeContext: URI,
	logName: Option[String],
	skipLogIngestionAtStart: Option[Boolean],
	logIngestionFromId: Option[Int],
	readContexts: Option[Seq[URI]],
	ingestion: Option[IngestionConfig]
)

case class DataObjectInstServerDefinition(label: String, format: URI, replayLogFrom: Option[Int] = None)

case class DataObjectInstServersConfig(
	commonReadContexts: Seq[URI],
	uriPrefix: URI,
	definitions: Seq[DataObjectInstServerDefinition]
)

case class InstanceServersConfig(
	specific: Map[String, InstanceServerConfig],
	forDataObjects: Map[Envri, DataObjectInstServersConfig],
	metaFlow: OptionalOneOrSeq[MetaFlowConfig]
)

sealed trait MetaFlowConfig:
	def cpMetaInstanceServerId: String

case class IcosMetaFlowConfig(
	cpMetaInstanceServerId: String,
	icosMetaInstanceServerId: String,
	otcMetaInstanceServerId: String,
	atcUpload: MetaUploadConf
) extends MetaFlowConfig

case class CitiesMetaFlowConfig(
	cpMetaInstanceServerId: String,
	citiesMetaInstanceServerId: String,
	munichUpload: MetaUploadConf,
	parisUpload: MetaUploadConf,
	zurichUpload: MetaUploadConf,
	atcUpload: MetaUploadConf
) extends MetaFlowConfig

case class MetaUploadConf(dirName: String, uploader: String)

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
	phenocamObjSpecId: String,
	metaService: URI,
	ingestFileMeta: Boolean
)

case class UploadServiceConfig(
	metaServers: Map[Envri, String],
	collectionServers: Map[Envri, String],
	documentServers: Map[Envri, String],
	handle: HandleNetClientConfig,
	etc: EtcConfig
)

case class LabelingServiceConfig(
	instanceServerId: String,
	provisionalInfoInstanceServerId: String,
	icosMetaInstanceServerId: String,
	tcUserIds: Map[URI, Seq[String]],
	dgUserId: String,
	riComEmail: String,
	calLabEmails: Seq[String],
	mailSendingActive: Boolean,
	mailing: EmailConfig,
	ontoId: String
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

case class RdfStorageConfig(
	lmdb: Option[LmdbConfig],
	path: String,
	recreateAtStartup: Boolean,
	indices: String,
	disableCpIndex: Boolean,
	recreateCpIndexAtStartup: Boolean
)

case class LmdbConfig(tripleDbSize: Long, valueDbSize: Long, valueCacheSize: Int)

case class CitationConfig(style: String, eagerWarmUp: Boolean, timeoutSec: Int, doi: DoiConfig)
case class DoiConfig(restEndpoint: URI, envries: Map[Envri, DoiMemberConfig]) extends DoiEndpointConfig

case class RestheartConfig(baseUri: String, dbNames: Map[Envri, String]) {
	def dbName(implicit envri: Envri): String = dbNames(envri)
}

case class StatsClientConfig(downloadsUri: String, previews: RestheartConfig)

case class CpmetaConfig(
	port: Int,
	httpBindInterface: String,
	dataUploadService: UploadServiceConfig,
	stationLabelingService: Option[LabelingServiceConfig],
	instanceServers: InstanceServersConfig,
	rdfLog: RdflogConfig,
	fileStoragePath: String,
	rdfStorage: RdfStorageConfig,
	onto: OntoConfig,
	auth: Map[Envri, PublicAuthConfig],
	core: MetaCoreConfig,
	sparql: SparqlServerConfig,
	citations: CitationConfig,
	statsClient: StatsClientConfig
)

object ConfigLoader extends CpmetaJsonProtocol:

	import MetaCoreConfig.given
	import DefaultJsonProtocol.*

	private val IcosFlow = "icos"
	private val CitiesFlow = "cities"

	given RootJsonFormat[IngestionMode] = enumFormat(IngestionMode.valueOf, IngestionMode.values)
	given RootJsonFormat[IngestionConfig] = jsonFormat3(IngestionConfig.apply)
	given RootJsonFormat[InstanceServerConfig] = jsonFormat6(InstanceServerConfig.apply)
	given RootJsonFormat[DataObjectInstServerDefinition] = jsonFormat3(DataObjectInstServerDefinition.apply)
	given RootJsonFormat[DataObjectInstServersConfig] = jsonFormat3(DataObjectInstServersConfig.apply)
	given RootJsonFormat[MetaUploadConf] = jsonFormat2(MetaUploadConf.apply)
	given RootJsonFormat[IcosMetaFlowConfig] = jsonFormat4(IcosMetaFlowConfig.apply)
	given RootJsonFormat[CitiesMetaFlowConfig] = jsonFormat6(CitiesMetaFlowConfig.apply)
	given RootJsonFormat[MetaFlowConfig] with
		def write(mfc: MetaFlowConfig): JsValue = mfc match
			case icos: IcosMetaFlowConfig => icos.toTypedJson(IcosFlow)
			case cities: CitiesMetaFlowConfig => cities.toTypedJson(CitiesFlow)

		def read(json: JsValue): MetaFlowConfig =
			json.asJsObject("Expected MetaFlowConfig to be a JSON object").fields.get(TypeField) match
				case Some(JsString(IcosFlow)) => json.convertTo[IcosMetaFlowConfig]
				case Some(JsString(CitiesFlow)) => json.convertTo[CitiesMetaFlowConfig]
				case Some(bad) => deserializationError(s"Unknown type of MetaFlowConfig: $bad")
				case None => deserializationError(s"Cannot deserialize as MetaFlowConfig, missing field $TypeField")

	given RootJsonFormat[InstanceServersConfig] = jsonFormat3(InstanceServersConfig.apply)
	given RootJsonFormat[DbServer] = jsonFormat2(DbServer.apply)
	given RootJsonFormat[DbCredentials] = jsonFormat3(DbCredentials.apply)
	given RootJsonFormat[RdflogConfig] = jsonFormat2(RdflogConfig.apply)
	given RootJsonFormat[PublicAuthConfig] = jsonFormat4(PublicAuthConfig.apply)
	given RootJsonFormat[SchemaOntologyConfig] = jsonFormat2(SchemaOntologyConfig.apply)
	given RootJsonFormat[InstOntoServerConfig] = jsonFormat4(InstOntoServerConfig.apply)
	given RootJsonFormat[OntoConfig] = jsonFormat2(OntoConfig.apply)
	given RootJsonFormat[DataSubmitterConfig] = jsonFormat6(DataSubmitterConfig.apply)
	given RootJsonFormat[SubmittersConfig] = jsonFormat1(SubmittersConfig.apply)
	given RootJsonFormat[EtcConfig] = jsonFormat7(EtcConfig.apply)
	given RootJsonFormat[HandleNetClientConfig] = jsonFormat6(HandleNetClientConfig.apply)

	given RootJsonFormat[UploadServiceConfig] = jsonFormat5(UploadServiceConfig.apply)
	import se.lu.nateko.cp.cpauth.core.JsonSupport.given RootJsonFormat[EmailConfig]
	given RootJsonFormat[LabelingServiceConfig] = jsonFormat10(LabelingServiceConfig.apply)
	given RootJsonFormat[SparqlServerConfig] = jsonFormat8(SparqlServerConfig.apply)
	given RootJsonFormat[LmdbConfig] = jsonFormat3(LmdbConfig.apply)
	given RootJsonFormat[RdfStorageConfig] = jsonFormat6(RdfStorageConfig.apply)
	given RootJsonFormat[DoiMemberConfig] = jsonFormat3(DoiMemberConfig.apply)
	given RootJsonFormat[DoiConfig] = jsonFormat2(DoiConfig.apply)
	given RootJsonFormat[CitationConfig] = jsonFormat4(CitationConfig.apply)
	given RootJsonFormat[RestheartConfig] = jsonFormat2(RestheartConfig.apply)
	given RootJsonFormat[StatsClientConfig] = jsonFormat2(StatsClientConfig.apply)

	given RootJsonFormat[CpmetaConfig] = jsonFormat14(CpmetaConfig.apply)

	lazy val default: CpmetaConfig = appConfig.getValue("cpmeta").parseAs[CpmetaConfig]

	private val submConfCache = WeakHashMap.empty[FileTime, SubmittersConfig]

	def submittersConfig: SubmittersConfig =
		val confFile = new java.io.File("submitters.conf").getAbsoluteFile
		if confFile.exists then
			val key = Files.getLastModifiedTime(confFile.toPath)
			submConfCache.getOrElseUpdate(
				key,
				ConfigFactory.parseFile(confFile).root.parseAs[SubmittersConfig]
			)
		else
			SubmittersConfig(Envri.values.iterator.map(_ -> Map.empty[String, DataSubmitterConfig]).toMap)

end ConfigLoader
