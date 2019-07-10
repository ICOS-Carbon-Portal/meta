package se.lu.nateko.cp.meta

import scala.concurrent.Future
import akka.http.scaladsl.model.Uri
import java.nio.file.Path
import java.nio.file.Paths
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import com.opencsv.CSVReader
import java.io.FileReader
import java.net.URI
import java.time.Instant
import scala.collection.JavaConverters.{asScalaIteratorConverter, enumerationAsScalaIteratorConverter}
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.LocalDateTime
import java.util.zip.ZipFile
import se.lu.nateko.cp.meta.core.data.TimeInterval
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.Done
import se.lu.nateko.cp.meta.utils.akkahttp.responseToDone
import akka.stream.ActorMaterializer
import se.lu.nateko.cp.meta.core.etcupload.StationId

object UploadWorkbench{
	implicit val system = ActorSystem("upload_workbench")
	implicit val mat = ActorMaterializer()
	import system.dispatcher
	val http = Http()
	val conf = new CpUploadClient.Config(
		//Uri("http://127.0.0.1:9094"),
		//Uri("http://127.0.0.1:9010")
		Uri("https://meta.icos-cp.eu"),
		Uri("https://data.icos-cp.eu")
	)

	var client: CpUploadClient = null

	def init(token: String): Unit = {
		client = new CpUploadClient(token, conf)
	}

	def uploadMeta(meta: Drought2018.FluxMeta): Future[Done] = client
		.objMetaUploadReq(Drought2018.makeDto(meta))
		.flatMap(req => http.singleRequest(req))
		.flatMap(responseToDone)

	def uploadFile(meta: Drought2018.FluxMeta): Future[Done] = http
		.singleRequest(client.fileUploadReq(meta.fileInfo))
		.flatMap(responseToDone)

	def uploadAll(): Unit = Drought2018.metas.foreach{meta =>
		val done = uploadMeta(meta).flatMap(_ => uploadFile(meta))
		done.failed.foreach{err => throw err}
	}

	def uploadEtcColl(): Future[Done] = client
		.collUploadReq(ETC_2019_1.collDto)
		.flatMap(req => http.singleRequest(req))
		.flatMap(responseToDone)

	def toCpDobj(hash: Sha256Sum): URI = toCpDobj(hash.id)
	def toCpDobj(suff: String): URI = new URI("https://meta.icos-cp.eu/objects/" + suff)
}

object ETC_2019_1{
	val ids = Seq("a3nJ2DqICphKnnJgTNc8NwUf", "ARCuA67fE8jQ5vLrfAMOnjZc", "exeQ2RM9MBXc72KMcl_h4C1W")

	def collDto = StaticCollectionDto(
		submitterId = "CP",
		members = ids.map(UploadWorkbench.toCpDobj).toIndexedSeq,
		title = "Ecosystem eddy covariance final quality (L2) flux product in ETC-Archive format - release 2019-1",
		description = Some("""This is the first release of the ICOS final quality data product for eddy covariance fluxes and meteorological observations at
 three labelled ICOS stations in the ecosystem domain. The archives contain more detailed description of the different data files contained in the archives.
Measurements have been collected using the following instructions:
# ICOS Ecosystem Instructions for Air Meteorological Measurements (TA, RH, PA, WS, WD), https://doi.org/10.18160/NHEG-4KWW
# ICOS Ecosystem Instructions for Turbulent Flux Measurements of CO2, Energy and Momentum, https://doi.org/10.18160/QWV4-639G"""
		),
		isNextVersionOf = None,
		preExistingDoi = Some("10.18160/NNAD-PN5W")
	)
}

object Drought2018{
	val baseDir = Paths.get("/home/oleg/workspace/cpupload/draught2018")
	val metaSrcPath = baseDir.resolve(Paths.get("fluxnetMetaSrc.csv"))
	def csv = new CSVReader(new FileReader(metaSrcPath.toFile))

	def metas: Iterator[FluxMeta] = csv.iterator().asScala.drop(1).map(parseFluxMeta)

	def getCollDto = StaticCollectionDto(
		submitterId = "CP",
		members = metas.map(meta => UploadWorkbench.toCpDobj(meta.hash)).toIndexedSeq,
		title = "Drought-2018 ecosystem eddy covariance flux product in FLUXNET-Archive format - release 2019-1",
		description = Some("This is the first public release of the observational data product for eddy covariance fluxes at 24 stations in the ecosystem domain from the Drought-2018 team, covering the period 1989-2018"),
		isNextVersionOf = None,
		preExistingDoi = Some("10.18160/PZDK-EF78")
	)

	def makeDto(meta: FluxMeta): ObjectUploadDto = {
		val productionDto = DataProductionDto(
			creator = new URI("http://meta.icos-cp.eu/resources/organizations/ETC"),
			contributors = Seq(meta.pi),
			hostOrganization = None,
			creationDate = meta.creationDate,
			comment = meta.comment
		)
		val stationMeta = StationDataMetadata(
			station = meta.station,
			instrument = None,
			samplingHeight = None,
			//acquisitionInterval = None,
			acquisitionInterval = Some(TimeInterval(meta.acqStart, meta.acqEnd)),
			nRows = meta.nPoints,
			production = Some(productionDto)
		)
		DataObjectDto(
			hashSum = meta.hash,
			submitterId = "CP",
			objectSpecification = new URI("http://meta.icos-cp.eu/resources/cpmeta/dought2018ArchiveProduct"),
//			objectSpecification = new URI("http://meta.icos-cp.eu/resources/cpmeta/drought2018FluxnetProduct"),
			fileName = meta.fname,
			specificInfo = Right(stationMeta),
			isNextVersionOf = None,
			preExistingDoi = Some("10.18160/" + DoiWorkbench.coolDoi(meta.hash))
		)
	}

	def parseFluxMeta(row: Array[String]) = new FluxMeta(
		hash = Sha256Sum.fromHex(row(0)).get,
		fname = row(1),
		nPoints = ifNotEmpty(row(2)).map(_.toInt - 1),
		isIcos = row(3) == "yes",
		pi = new URI("http://meta.icos-cp.eu/resources/people/" + row(4)),
		ack = ifNotEmpty(row(5)),
		papers = ifNotEmpty(row(6)).toSeq ++ ifNotEmpty(row(7))
	)

	private def ifNotEmpty(s: String): Option[String] = Option(s).map(_.trim).filter(_.length > 0)

	class FluxMeta(
		val hash: Sha256Sum, val fname: String, val nPoints: Option[Int], val isIcos: Boolean,
		val pi: URI, val ack: Option[String], val papers: Seq[String]
	){

		val StationId(stationId) = fname.substring(4, 10)

		def station: URI = {
			val pref = if(isIcos) "ES_" else "FLUXNET_"
			new URI(s"http://meta.icos-cp.eu/resources/stations/$pref${stationId.id}")
		}

		def yearFrom = fname.substring(31, 35).toInt
		def yearTo = fname.substring(36, 40).toInt

		def acqStart = Instant.parse(
			s"${yearFrom}-01-01T00:00:00Z"
		)

		def acqEnd = Instant.parse(
			s"${yearTo + 1}-01-01T00:00:00Z"
		)

		def comment: Option[String] = if(ack.isEmpty && papers.isEmpty) None else Some{
			val ref: Option[String] = if(papers.isEmpty) None else Some("See also:")
			(ack.toSeq ++ ref.toSeq ++ papers).mkString("\n")
		}

		def fileInfo = new CpUploadClient.FileInfo(filePath, hash)

		def filePath: Path = baseDir.resolve(Paths.get(fname))

		def creationDate = Instant.ofEpochMilli(
			new ZipFile(filePath.toFile).entries().asScala.map(_.getTime).max
		)

	}
}
