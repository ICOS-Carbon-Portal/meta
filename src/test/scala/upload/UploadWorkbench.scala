package se.lu.nateko.cp.meta.upload

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import se.lu.nateko.cp.doi.*
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.services.citation.CitationClientImpl
import se.lu.nateko.cp.meta.upload.drought.{DroughtDoiMaker, DroughtDoiMaker2, FluxdataUpload}
import se.lu.nateko.cp.meta.utils.async.executeSequentially

import java.net.URI
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object UploadWorkbench{
	given system: ActorSystem = ActorSystem("upload_workbench")
	import system.dispatcher

	val uploadConfBase = new CpUploadClient.Config(
		"???",
		"meta.icos-cp.eu",
		"data.icos-cp.eu"
		//Some(Uri("http://127.0.0.1:9094")),
		//Some(Uri("http://127.0.0.1:9010"))
	)

	val metaConf = se.lu.nateko.cp.meta.ConfigLoader.default

	def atcColMaker(datacitePass: String, cpauthToken: String) =
		new AtcCollMaker(new DoiMaker(datacitePass), uploadClient(cpauthToken))

	val citer = CitationClientImpl(Nil, metaConf.citations, TrieMap.empty, TrieMap.empty)
	def uploadClient(cpAuthToken: String) = new CpUploadClient(uploadConfBase.copy(cpauthToken = cpAuthToken))

	private def atmoUpload = FluxdataUpload.atmoUpload(citer)
	private def fluxHhUpload = FluxdataUpload.fluxHhUpload(citer)
	private def fluxUpload = FluxdataUpload.fluxUpload(citer)

	private def doiMachinery(password: String): (DoiMaker, DroughtDoiMaker2) = {
		val client = new DoiMaker(password)
		val maker = new DroughtDoiMaker2(client, citer)
		client -> maker
	}

	def uploadAtmoHistoric(cpAuthToken: String): Future[Done] = uploadHistoric(cpAuthToken, atmoUpload)
	def uploadFluxHhHistoric(cpAuthToken: String): Future[Done] = uploadHistoric(cpAuthToken, fluxHhUpload)
	def uploadFluxHistoric(cpAuthToken: String): Future[Done] = uploadHistoric(cpAuthToken, fluxUpload)

	private def uploadHistoric(cpAuthToken: String, upload: FluxdataUpload): Future[Done] = {
		val client = uploadClient(cpAuthToken)
		executeSequentially(upload.uploadedFileMetaEntries){fe =>
			val finfo = upload.getFileInfo(fe)
			upload.makeDto(fe).flatMap{dto =>
				println(s"Will upload ${finfo.hash.id} (${finfo.path})")
				client.uploadSingleObject(dto, finfo)
			}
		}
	}

	def uploadAtmoColl(client: CpUploadClient): Future[Done] = client.uploadSingleCollMeta(atmoUpload.getAtmoCollDto)
	def uploadFluxColl(client: CpUploadClient): Future[Done] = client.uploadSingleCollMeta(fluxUpload.getFluxCollDto)

	def registerAtmoCollDoi(password: String): Future[Done] = {
		val (client, maker) = doiMachinery(password)
		val doiMeta = maker.atmoCollDoiMeta("ERE9-9D85", atmoUpload.allFileMetaEntries).copy(
			url = Some("https://meta.icos-cp.eu/collections/yZecOZ-jPa8nw8JVOTHtlaYN")
		)
		client.saveDoi(doiMeta)
	}

	def registerFluxCollDoi(password: String): Future[Done] = {
		val (client, maker) = doiMachinery(password)
		val doiMeta = maker.fluxCollDoiMeta("2G60-ZHAK", fluxUpload.allFileMetaEntries).copy(
			url = Some("https://meta.icos-cp.eu/collections/gdINRHdRH6xknqoLsIU1FOZ4")
		)
		client.saveDoi(doiMeta)
	}

	def registerAtmoDois(password: String): Future[Done] = {
		val (_, maker) = doiMachinery(password)
		maker.publishDois(atmoUpload.uploadedFileMetaEntries, maker.makeAtmoDoiMeta)
	}

	def registerFluxDois(password: String): Future[Done] = {
		val (_, maker) = doiMachinery(password)
		maker.publishDois(fluxUpload.uploadedFileMetaEntries, maker.makeFluxDoiMeta)
	}

	def droughtDoiMaker(password: String): DroughtDoiMaker = Await.result(
		DroughtDoiMaker(new DoiMaker(password)),
		5.seconds
	)

	def toCpDobj(hash: Sha256Sum): URI = toCpDobj(hash.id)
	def toCpDobj(suff: String): URI = new URI(uploadConfBase.metaBase.toString + "/objects/" + suff)
}

object ETC_2019_1{
	val ids = Seq("a3nJ2DqICphKnnJgTNc8NwUf", "ARCuA67fE8jQ5vLrfAMOnjZc", "exeQ2RM9MBXc72KMcl_h4C1W")

	def uploadColl(client: CpUploadClient) = client.uploadSingleCollMeta(collDto)

	def collDto = StaticCollectionDto(
		submitterId = "CP",
		members = ids.map(UploadWorkbench.toCpDobj).toIndexedSeq,
		title = "Ecosystem eddy covariance final quality (L2) flux product in ETC-Archive format - release 2019-1",
		description = Some("""This is the first release of the ICOS final quality data product for eddy covariance fluxes and meteorological observations at
 three labelled ICOS stations in the ecosystem domain. The archives contain more detailed description of the different data files included in them.
Measurements have been collected using the following instructions:
# ICOS Ecosystem Instructions for Air Meteorological Measurements (TA, RH, PA, WS, WD), https://doi.org/10.18160/NHEG-4KWW
# ICOS Ecosystem Instructions for Turbulent Flux Measurements of CO2, Energy and Momentum, https://doi.org/10.18160/QWV4-639G"""
		),
		isNextVersionOf = None,
		preExistingDoi = Some(Doi("10.18160", "NNAD-PN5W")),
		documentation = None,
		coverage = None
	)
}

