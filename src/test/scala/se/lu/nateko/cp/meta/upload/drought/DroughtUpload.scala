package se.lu.nateko.cp.meta.upload.drought

import scala.concurrent.Future
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.URI
import java.nio.file.Path
import se.lu.nateko.cp.meta.core.data.TimeInterval
import akka.Done
import java.io.InputStreamReader
import com.opencsv.CSVReader
import java.nio.file.Paths
import scala.collection.JavaConverters.asScalaIteratorConverter
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.DataProductionDto
import se.lu.nateko.cp.meta.StationDataMetadata
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.upload._


object DroughtUpload{
	val baseDir = Paths.get("/home/oleg/workspace/cpupload/draught2018")
	val archiveSpec = new URI("http://meta.icos-cp.eu/resources/cpmeta/dought2018ArchiveProduct")
	val hhSpec = new URI("http://meta.icos-cp.eu/resources/cpmeta/drought2018FluxnetProduct")

	def archiveMetas = metas(baseDir, "fluxnetMetaSrc.csv")
	def hhMetas = metas(baseDir.resolve("hh/"), "fluxnetHhMetaSrc.csv")

	def archiveObjInfos: Iterator[CpUploadClient.ObjectUploadInfo] = archiveMetas.map{meta =>
		makeDto(meta, archiveSpec) -> meta.fileInfo
	}

	def hhObjInfos: Iterator[CpUploadClient.ObjectUploadInfo] = hhMetas.map{meta =>
		makeDto(meta, hhSpec) -> meta.fileInfo
	}

	def metas(filesFolder: Path, metaSrcFileName: String): Iterator[FluxMeta] = {
		val metaSrc = getClass.getClassLoader.getResourceAsStream(metaSrcFileName)
		val csv = new CSVReader(new InputStreamReader(metaSrc))
		csv.iterator().asScala.drop(1).map(parseFluxMeta(_, filesFolder))
	}

	def uploadCollection(client: CpUploadClient): Future[Done] = client.uploadSingleCollMeta(getCollDto)

	def getCollDto = StaticCollectionDto(
		submitterId = "CP",
		members = archiveMetas.map(meta => UploadWorkbench.toCpDobj(meta.hash)).toIndexedSeq,
		title = "Drought-2018 ecosystem eddy covariance flux product in FLUXNET-Archive format - release 2019-1",
		description = Some("This is the first public release of the observational data product for eddy covariance fluxes at 27 stations in the ecosystem domain from the Drought-2018 team, covering the period 1989-2018"),
		isNextVersionOf = None,
		preExistingDoi = Some("10.18160/PZDK-EF78")
	)

	def makeDto(meta: FluxMeta, spec: URI): ObjectUploadDto = {
		val productionDto = DataProductionDto(
			creator = new URI("http://meta.icos-cp.eu/resources/organizations/ETC"),
			contributors = Seq(meta.pi),
			hostOrganization = None,
			creationDate = meta.creationDate,
			sources = None,
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
			objectSpecification = spec,
			fileName = meta.fname,
			specificInfo = Right(stationMeta),
			isNextVersionOf = None,
			preExistingDoi = Some("10.18160/" + DoiMaker.coolDoi(meta.hash))
		)
	}

	def parseFluxMeta(row: Array[String], base: Path) = new FluxMeta(
		hash = Sha256Sum.fromHex(row(0)).get,
		filePath = base.resolve(row(1)),
		nPoints = ifNotEmpty(row(2)).map(_.toInt - 1),
		isIcos = row(3) == "yes",
		pi = new URI("http://meta.icos-cp.eu/resources/people/" + row(4)),
		ack = ifNotEmpty(row(5)),
		papers = ifNotEmpty(row(6)).toSeq ++ ifNotEmpty(row(7))
	)

	private def ifNotEmpty(s: String): Option[String] = Option(s).map(_.trim).filter(_.length > 0)

}
