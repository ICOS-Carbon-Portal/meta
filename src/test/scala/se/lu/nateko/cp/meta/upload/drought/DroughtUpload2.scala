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
import se.lu.nateko.cp.doi._
import se.lu.nateko.cp.meta.api.CitationClient
import scala.concurrent.ExecutionContext
import java.nio.file.Files
import se.lu.nateko.cp.meta.upload.CpUploadClient.FileInfo


object DroughtUpload2{
	val baseDir = Paths.get("/home/oleg/workspace/cpupload/drought2018/v2")
	val fluxnetArchiveSpec = new URI("http://meta.icos-cp.eu/resources/cpmeta/dought2018ArchiveProduct")
	val fluxnetHhSpec = new URI("http://meta.icos-cp.eu/resources/cpmeta/drought2018FluxnetProduct")
	val atmoSpec = new URI("http://meta.icos-cp.eu/resources/cpmeta/drought2018AtmoProduct")

	val atcOrg = new URI("http://meta.icos-cp.eu/resources/organizations/ATC")
	val etcOrg = new URI("http://meta.icos-cp.eu/resources/organizations/ETC")

	def atmoUpload(citer: CitationClient)(implicit ctxt: ExecutionContext) = new DroughtUpload2("atmos_data.csv", atmoSpec, citer)
	def fluxHhUpload(citer: CitationClient)(implicit ctxt: ExecutionContext) = new DroughtUpload2("eco_data_hh.csv", fluxnetHhSpec, citer)
	def fluxUpload(citer: CitationClient)(implicit ctxt: ExecutionContext) = new DroughtUpload2("eco_data_arch.csv", fluxnetArchiveSpec, citer)
}


class DroughtUpload2(
	fileEntriesFile: String, spec: URI,
	citer: CitationClient
)(implicit ctxt: ExecutionContext){

	import DroughtUpload2._
	import DroughtMeta2._

	private val haveDois: Boolean = (spec != fluxnetHhSpec)
	private val project: String = if(spec == atmoSpec) Atmo else Fluxnet

	val fileMetaEntries: IndexedSeq[FileEntry] = {

		def metaFile(fname: String) = baseDir.resolve(s"meta/$fname").toFile

		val affils = parseAffiliations(metaFile("affiliation.csv"))
		val persons = parsePersons(metaFile("persons.csv"), affils)
		parseFileEntries(metaFile(fileEntriesFile), project, persons)
	}

	//def uploadFluxCollection(client: CpUploadClient): Future[Done] = client.uploadSingleCollMeta(getFluxCollDto)

	// def getFluxCollDto = StaticCollectionDto(
	// 	submitterId = "CP",
	// 	members = archiveMetas.map(meta => UploadWorkbench.toCpDobj(meta.hash)).toIndexedSeq,
	// 	title = "Drought-2018 ecosystem eddy covariance flux product in FLUXNET-Archive format - release 2019-1",
	// 	description = Some("This is the first public release of the observational data product for eddy covariance fluxes at 30 stations in the ecosystem domain from the Drought-2018 team, covering the period 1989-2018. Updates since the previous version of this collection: 3 more stations added (CH-Lae, CH-Oe2, FI-Let), and data from ES-LM2 and FI-Sii have been updated."),
	// 	isNextVersionOf = Some(Left(Sha256Sum.fromBase64Url("681IIgBN34OEbfWwVU_IWlwA").get)),
	// 	preExistingDoi = Some(Doi("10.18160", "PZDK-EF78"))
	// )

	def getFilePath(meta: FileEntry): Path = baseDir.resolve(project.toLowerCase).resolve(meta.fileName)
	def getFileInfo(meta: FileEntry) = new FileInfo(getFilePath(meta), meta.hash)

	def makeDto(meta: FileEntry): Future[ObjectUploadDto] = meta.comment(citer).map{comment =>

		val productionDto = DataProductionDto(
			creator = meta.creatorUrl,
			contributors = (meta.authors ++ meta.contribs).map(p => new URI(p.url)),
			hostOrganization = None,
			creationDate = Files.getLastModifiedTime(getFilePath(meta)).toInstant,
			sources = None,
			comment = comment
		)

		val stationMeta = StationDataMetadata(
			station = meta.stationUrl,
			site = None,
			instrument = None,
			samplingHeight = samplingHeightOpt(meta),
			acquisitionInterval = timeIntervalOpt(meta),
			nRows = npointsOpt(meta),
			production = Some(productionDto)
		)

		DataObjectDto(
			hashSum = meta.hash,
			submitterId = "CP",
			objectSpecification = spec,
			fileName = meta.fileName,
			specificInfo = Right(stationMeta),
			isNextVersionOf = meta.prevHash.map(Left(_)),
			preExistingDoi = if(haveDois) Some(Doi("10.18160", DoiMaker.coolDoi(meta.hash))) else None
		)
	}

	def timeIntervalOpt(fe: FileEntry): Option[TimeInterval] =
		if(spec == fluxnetArchiveSpec) Some(fluxTimeInterval(fe)) else None

	def npointsOpt(fe: FileEntry): Option[Int] =
		if(spec == fluxnetHhSpec) fe.nPoints else None
}
