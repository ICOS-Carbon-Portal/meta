package se.lu.nateko.cp.meta.upload

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.URI
import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.StaticCollectionDto
import scala.concurrent.Await
import se.lu.nateko.cp.meta.upload.drought.DroughtDoiMaker
import scala.concurrent.duration.DurationInt

object UploadWorkbench{
	implicit val system = ActorSystem("upload_workbench")
	import system.dispatcher

	val uploadConfBase = new CpUploadClient.Config(
		"???",
		"meta.icos-cp.eu",
		"data.icos-cp.eu"
		//Some(Uri("http://127.0.0.1:9094")),
		//Some(Uri("http://127.0.0.1:9010"))
	)

	def uploadClient(cpAuthToken: String) = new CpUploadClient(uploadConfBase.copy(cpauthToken = cpAuthToken))

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
		preExistingDoi = Some("10.18160/NNAD-PN5W")
	)
}

