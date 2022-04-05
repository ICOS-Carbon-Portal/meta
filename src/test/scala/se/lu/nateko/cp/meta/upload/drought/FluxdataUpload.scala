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
import scala.jdk.CollectionConverters._
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.DataProductionDto
import se.lu.nateko.cp.meta.StationTimeSeriesDto
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.doi._
import se.lu.nateko.cp.meta.services.citation.CitationClient
import scala.concurrent.ExecutionContext
import java.nio.file.Files
import se.lu.nateko.cp.meta.upload.CpUploadClient.FileInfo
import java.util.zip.ZipFile
import java.time.Instant
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.ReferencesDto


object FluxdataUpload{
	val baseDir = Paths.get("/home/oleg/workspace/cpupload/icosHistorical/v2")
	val fluxnetArchiveSpec = new URI("http://meta.icos-cp.eu/resources/cpmeta/miscFluxnetArchiveProduct")
	val fluxnetHhSpec = new URI("http://meta.icos-cp.eu/resources/cpmeta/miscFluxnetProduct")
	val atmoSpec = new URI("http://meta.icos-cp.eu/resources/cpmeta/drought2018AtmoProduct")

	val atcOrg = new URI("http://meta.icos-cp.eu/resources/organizations/ATC")
	val etcOrg = new URI("http://meta.icos-cp.eu/resources/organizations/ETC")

	def atmoUpload(citer: CitationClient)(implicit ctxt: ExecutionContext) = new FluxdataUpload("atmos_data.csv", atmoSpec, citer)
	def fluxHhUpload(citer: CitationClient)(implicit ctxt: ExecutionContext) = new FluxdataUpload("winter_final_hh.csv", fluxnetHhSpec, citer)
	def fluxUpload(citer: CitationClient)(implicit ctxt: ExecutionContext) = new FluxdataUpload("winter_final_arch.csv", fluxnetArchiveSpec, citer)

	val excludedUploadStations: Set[String] = Set(
		//"CH-Cha"
		//"IE-Cra", "DE-RuR", "DE-RuS", "DE-RuW", "IL-Yat"
	)
	val succeededStationsToSkip = 0
}


class FluxdataUpload(
	fileEntriesFile: String, spec: URI,
	citer: CitationClient
)(implicit ctxt: ExecutionContext){

	import FluxdataUpload._
	import DroughtMeta2._

	private val haveDois: Boolean = (spec != fluxnetHhSpec)
	private val project: Project = if(spec == atmoSpec) Atmo else (
		if(spec == fluxnetHhSpec) Winter2020Hh else Winter2020
	)

	def uploadedFileMetaEntries: IndexedSeq[FileEntry] = allFileMetaEntries.filter{fe =>
		val sameVersion = fe.prevHash.contains(fe.hash)
		//if(sameVersion) println(s"New version of itself: ${fe.fileName}")
		!excludedUploadStations.contains(fe.fileName.substring(4, 10)) &&
		!sameVersion
	}.drop(succeededStationsToSkip)

	val allFileMetaEntries: IndexedSeq[FileEntry] = {

		def metaFile(fname: String) = baseDir.resolve(s"meta/$fname").toFile

		val affils = parseAffiliations(metaFile("affiliation.csv"))
		val persons = parsePersons(metaFile("persons.csv"), affils)
		parseFileEntries(metaFile(fileEntriesFile), project, persons)
	}

	//def uploadFluxCollection(client: CpUploadClient): Future[Done] = client.uploadSingleCollMeta(getFluxCollDto)

	def getAtmoCollDto = StaticCollectionDto(
		submitterId = "CP",
		members = allFileMetaEntries.map(meta => UploadWorkbench.toCpDobj(meta.hash)).toIndexedSeq,
		title = "Drought-2018 atmospheric CO2 Mole Fraction product for 48 stations (96 sample heights)—release 2019-1",
		description = Some("Atmospheric Greenhouse Gas Mole Fractions of CO2 collected by the Drought-2018 team, covering the period 1979-2018. Final quality controlled Level 2 data, release 2019-1. During the most recent period,  a selected set of stations, after being labelled as ICOS stations, follow the ICOS Atmospheric Station specification V1.3 (https://www.icos-ri.eu/fetch/ba12290c-3714-4dd5-a9f0-c431b9900ad1;1.0). Measurements and data processing for all time series is described in Ramonet, 2019 (doi:xxxxx). All concentrations are calibrated to the WMO X2007 CO2 mole fraction scale in µmole/mole (ppm)."),
		isNextVersionOf = None,
		preExistingDoi = Some(Doi("10.18160", "ERE9-9D85"))
	)

	def getFluxCollDto = StaticCollectionDto(
		submitterId = "CP",
		members = allFileMetaEntries.map(meta => UploadWorkbench.toCpDobj(meta.hash)).toIndexedSeq,
		title = "Warm Winter 2020 ecosystem eddy covariance flux product for 73 stations in FLUXNET-Archive format—release 2022-1",
		description = Some("This is the release of the observational data product for eddy covariance fluxes at 73 stations in the ecosystem domain, part of them outside the ICOS network, covering the period 1989-2020. The data are in the standard format used for the ICOS L2 ecosystem products and also used by other regional networks like AmeriFlux. The processing has been done using the ONEFlux processing pipeline (https://github.com/icos-etc/ONEFlux) and is fully compliant and integrable with the FLUXNET2015 release (https://fluxnet.fluxdata.org/) and other datasets processed with the same pipeline (AmeriFlux, ICOS L2)."),
		isNextVersionOf = None,//Some(Left(Sha256Sum.fromBase64Url("UZw8ra7OVilmVjATTCgIimpz").get)),
		preExistingDoi = Some(Doi("10.18160", "2G60-ZHAK"))
	)

	def getFilePath(meta: FileEntry): Path = baseDir
		.resolve(project.getClass.getName.split("\\$").last.toLowerCase)
		.resolve(meta.fileName)
	def getFileInfo(meta: FileEntry) = new FileInfo(getFilePath(meta), meta.hash)

	def makeDto(meta: FileEntry): Future[ObjectUploadDto] = meta.comment(citer).map{comment =>

		val productionDto = DataProductionDto(
			creator = meta.creatorUrl,
			contributors = (meta.authors ++ meta.contribs).map(p => new URI(p.url)),
			hostOrganization = None,
			creationDate = if(meta.fileName.endsWith(".zip")) Instant.ofEpochMilli(
					new ZipFile(getFilePath(meta).toFile).entries().asScala.map(_.getTime).max
				) else
					Files.getLastModifiedTime(getFilePath(meta)).toInstant,
			sources = None,
			documentation = None,
			comment = comment
		)

		val stationMeta = StationTimeSeriesDto(
			station = meta.stationUrl,
			site = None,
			instrument = None,
			samplingPoint = None,
			samplingHeight = samplingHeightOpt(meta),
			acquisitionInterval = timeIntervalOpt(meta),
			nRows = meta.nPoints,
			production = Some(productionDto)
		)

		DataObjectDto(
			hashSum = meta.hash,
			submitterId = "CP",
			objectSpecification = spec,
			fileName = meta.fileName,
			specificInfo = Right(stationMeta),
			isNextVersionOf = meta.prevHash.map(Left(_)),
			preExistingDoi = if(haveDois) Some(Doi("10.18160", DoiMaker.coolDoi(meta.hash))) else None,
			references = Some(ReferencesDto(Some(Seq("Warm Winter 2020")), None, None))
		)
	}

	def timeIntervalOpt(fe: FileEntry): Option[TimeInterval] =
		if(spec == fluxnetArchiveSpec) Some(fluxTimeInterval(fe)) else None

}
