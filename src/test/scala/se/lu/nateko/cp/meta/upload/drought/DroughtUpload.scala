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
import scala.jdk.CollectionConverters.IteratorHasAsScala
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.DataProductionDto
import se.lu.nateko.cp.meta.StationDataMetadata
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.doi._


object DroughtUpload{
	val baseDir = Paths.get("/home/oleg/workspace/cpupload/drought2018/v2")
	val archiveSpec = new URI("http://meta.icos-cp.eu/resources/cpmeta/dought2018ArchiveProduct")
	val hhSpec = new URI("http://meta.icos-cp.eu/resources/cpmeta/drought2018FluxnetProduct")

	def archiveMetas = metas(baseDir, "fluxnetMetaSrc.csv")
	def hhMetas = metas(baseDir.resolve("hh/"), "fluxnetHhMetaSrc.csv")

	def archiveObjInfos: Iterator[CpUploadClient.ObjectUploadInfo] = archiveMetas.map{meta =>
		makeDto(meta, archiveSpec, false) -> meta.fileInfo
	}

	def hhObjInfos: Iterator[CpUploadClient.ObjectUploadInfo] = hhMetas.map{meta =>
		makeDto(meta, hhSpec, true) -> meta.fileInfo
	}

	def metas(filesFolder: Path, metaSrcFileName: String): Iterator[FluxMeta] = {
		val metaSrc = getClass.getClassLoader.getResourceAsStream(metaSrcFileName)
		val csv = new CSVReader(new InputStreamReader(metaSrc))
		csv.iterator().asScala.drop(1).map(parseFluxMeta(_, filesFolder))
	}

	def fileMetaEntries(fileName: String, project: String): IndexedSeq[FileEntry] = {
		import DroughtMeta2._

		def metaFile(fname: String) = baseDir.resolve(s"meta/$fname").toFile

		val affils = parseAffiliations(metaFile("affiliation.csv"))
		val persons = parsePersons(metaFile("persons.csv"), affils)
		parseFileEntries(metaFile(fileName), project, persons)
	}

	def uploadFluxCollection(client: CpUploadClient): Future[Done] = client.uploadSingleCollMeta(getFluxCollDto)

	def getFluxCollDto = StaticCollectionDto(
		submitterId = "CP",
		members = archiveMetas.map(meta => UploadWorkbench.toCpDobj(meta.hash)).toIndexedSeq,
		title = "Drought-2018 ecosystem eddy covariance flux product in FLUXNET-Archive format - release 2019-1",
		description = Some("This is the first public release of the observational data product for eddy covariance fluxes at 30 stations in the ecosystem domain from the Drought-2018 team, covering the period 1989-2018. Updates since the previous version of this collection: 3 more stations added (CH-Lae, CH-Oe2, FI-Let), and data from ES-LM2 and FI-Sii have been updated."),
		isNextVersionOf = Some(Left(Sha256Sum.fromBase64Url("681IIgBN34OEbfWwVU_IWlwA").get)),
		preExistingDoi = Some(Doi("10.18160", "PZDK-EF78"))
	)

	def makeDto(meta: FluxMeta, spec: URI, isHh: Boolean): ObjectUploadDto = {
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
			site = None,
			instrument = None,
			samplingHeight = None,
			acquisitionInterval = if(isHh) None else Some(TimeInterval(meta.acqStart, meta.acqEnd)),
			nRows = meta.nPoints,
			production = Some(productionDto)
		)
		DataObjectDto(
			hashSum = meta.hash,
			submitterId = "CP",
			objectSpecification = spec,
			fileName = meta.fname,
			specificInfo = Right(stationMeta),
			isNextVersionOf = meta.prevVers.map(Left(_)),
			preExistingDoi = if(isHh) None else Some(Doi("10.18160", DoiMaker.coolDoi(meta.hash))),
			references = None
		)
	}

	def parseFluxMeta(row: Array[String], base: Path) = new FluxMeta(
		hash = Sha256Sum.fromHex(row(0)).get,
		filePath = base.resolve(row(2)),
		nPoints = ifNotEmpty(row(3)).map(_.toInt - 1),
		isIcos = row(4) == "yes",
		pi = new URI("http://meta.icos-cp.eu/resources/people/" + row(5)),
		ack = ifNotEmpty(row(6)),
		papers = ifNotEmpty(row(7)).toSeq ++ ifNotEmpty(row(8)),
		prevVers = Sha256Sum.fromHex(row(1)).toOption
	)

	def ifNotEmpty(s: String): Option[String] = Option(s).map(_.trim).filter(_.length > 0)

}
