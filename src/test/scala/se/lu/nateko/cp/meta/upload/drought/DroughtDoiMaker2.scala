package se.lu.nateko.cp.meta.upload.drought

import se.lu.nateko.cp.doi.core.DoiClient
import se.lu.nateko.cp.doi._
import se.lu.nateko.cp.doi.meta._
import scala.concurrent.Future
import se.lu.nateko.cp.meta.core.etcupload.StationId
import java.net.URI
import scala.concurrent.ExecutionContext
import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.meta.utils.async.executeSequentially
import akka.Done
import java.time.Instant
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.api.CitationClient


class DroughtDoiMaker2(maker: DoiMaker, citer: CitationClient)(implicit ctxt: ExecutionContext){
	import DroughtDoiMaker2._
	import DoiMaker._

	def publishDoi(doiMeta: DoiMeta, objHash: Sha256Sum): Future[Doi] = {
		val target = UploadWorkbench.toCpDobj(objHash)
		maker.setDoi(doiMeta -> target).map(_ => doiMeta.id)
	}

	def publishDois(metas: Seq[FileEntry], doiMetaMaker: FileEntry => Future[DoiMeta]): Future[Done] =
		executeSequentially(metas){meta =>
			doiMetaMaker(meta).flatMap(publishDoi(_, meta.hash)).map{doi =>
				println(s"Success for ${meta.stationId}: $doi")
				Done
			}
		}

	def makeFluxDoiMeta(meta: FileEntry): Future[DoiMeta] = meta.comment(citer).map{commentOpt =>
		val stationName = meta.stationName

		val (yearFrom, yearTo) = DroughtMeta2.fluxFileYears(meta)

		val title = Title(s"Drought-2018 ecosystem eddy covariance flux product from " + stationName, None, None)
		val descr = "Public release of the observational data product for eddy covariance fluxes " +
			s"at $stationName, covering the period $yearFrom-$yearTo"
		val majorVersion = if(meta.prevHash.isDefined) 2 else 1

		DoiMeta(
			id = maker.client.doi(coolDoi(meta.hash)),
			creators = meta.authors.map(creatorPerson) :+ etcCreator,
			titles = Seq(title),
			publisher = "ICOS Carbon Portal",
			publicationYear = 2020,
			resourceType = ResourceType("FLUXNET zip archive", ResourceTypeGeneral.Dataset),
			subjects = Seq(
				Subject("Biogeochemical cycles, processes, and modeling"),
				Subject("Troposphere: composition and chemistry")
			),
			contributors = (
					meta.contribs.map(dataCollectorPerson) :+
					contributorStation(stationName, meta.stationId, ContributorType.DataCollector) :+
					etcContrib
				) ++ etcPeople,
			dates = Seq(
				Date(meta.creationDate.toString, DateType.Created)
			),
			formats = Seq("ZIP archive with ASCII CSV files"),
			version = Some(Version(majorVersion, 0)),
			rights = Seq(cc4by),
			descriptions = Seq(Description(descr, DescriptionType.Abstract, None)) ++ commentOpt.toSeq.map(comm =>
				Description(comm, DescriptionType.Other, None)
			)
		)
	}

	def makeAtmoDoiMeta(meta: FileEntry): Future[DoiMeta] = meta.comment(citer).map{commentOpt =>
		val stationName = meta.stationName

		val title = Title(s"Drought-2018 CO2 molar fraction product from " + stationName, None, None)
		val descr = s"Public release of the observational data product for atmospheric CO2 molar fraction at $stationName"

		DoiMeta(
			id = maker.client.doi(coolDoi(meta.hash)),
			creators = meta.authors.map(creatorPerson) :+ atcCreator,
			titles = Seq(title),
			publisher = "ICOS Carbon Portal",
			publicationYear = 2020,
			resourceType = ResourceType("ICOS ATC time series", ResourceTypeGeneral.Dataset),
			subjects = Seq(
				Subject("Carbon dioxide")
			),
			contributors = (
					meta.contribs.map(dataCollectorPerson) :+
					contributorStation(stationName, meta.stationId, ContributorType.DataCollector) :+
					atcContrib
				) ++ atcPeople,
			dates = Seq(
				Date(meta.creationDate.toString, DateType.Created)
			),
			formats = Seq("ZIP archive with ASCII CSV files"),
			version = Some(Version(1, 0)),
			rights = Seq(cc4by),
			descriptions = Seq(Description(descr, DescriptionType.Abstract, None)) ++ commentOpt.toSeq.map(comm =>
				Description(comm, DescriptionType.Other, None)
			)
		)
	}

	def fluxCollDoiMeta(suffix: String, metas: IndexedSeq[FileEntry]): DoiMeta = {
		val contribs = metas.flatMap(fe => fe.authors ++ fe.contribs).groupBy(_.id).map{
			case (_, pes) => dataCollectorPerson(pes.head)
		}.toSeq
		val title = Title("Drought-2018 ecosystem eddy covariance flux product in FLUXNET-Archive format—release 2019-2", None, None)
		val descr = s"Public release of the observational data product for eddy covariance fluxes at ${metas.size} stations " +
			"in the ecosystem domain from the Drought-2018 team, covering the period 1989-2018."
		DoiMeta(
			id = maker.client.doi(suffix),
			creators = Seq(Creator(GenericName("Drought 2018 Team"), Nil, Nil), etcCreator),
			titles = Seq(title),
			publisher = "ICOS Carbon Portal",
			publicationYear = 2020,
			resourceType = ResourceType("ZIP archives", ResourceTypeGeneral.Collection),
			subjects = Seq(
				Subject("Biogeochemical cycles, processes, and modeling"),
				Subject("Troposphere: composition and chemistry")
			),
			contributors = ((etcContrib +: contribs) ++ etcPeople).distinct,
			dates = Seq(
				Date(Instant.now.toString.take(10), DateType.Issued)
			),
			formats = Seq("Collection of FLUXNET product ZIP archives"),
			version = Some(Version(2, 0)),
			rights = Seq(cc4by),
			descriptions = Seq(Description(descr, DescriptionType.Abstract, None))
		)
	}

	def atmoCollDoiMeta(suffix: String, metas: IndexedSeq[FileEntry]): DoiMeta = {
		val contribs = metas.flatMap(fe => fe.authors ++ fe.contribs).groupBy(_.id).map{
			case (_, pes) => dataCollectorPerson(pes.head)
		}.toSeq
		val title = Title("Drought-2018 atmospheric CO2 Mole Fraction product for 48 stations (96 sample heights)—release 2019-1", None, None)
		val descr = s"Atmospheric Greenhouse Gas Mole Fractions of CO2 collected by the Drought-2018 team, covering the period 1979-2018. Final quality controlled Level 2 data, release 2019-1. During the most recent period,  a selected set of stations, after being labelled as ICOS stations, follow the ICOS Atmospheric Station specification V1.3 (https://www.icos-ri.eu/fetch/ba12290c-3714-4dd5-a9f0-c431b9900ad1;1.0). Measurements and data processing for all time series is described in Ramonet, 2019 (doi:xxxxx). All concentrations are calibrated to the WMO X2007 CO2 mole fraction scale in µmole/mole (ppm)."

		DoiMeta(
			id = maker.client.doi(suffix),
			creators = Seq(Creator(GenericName("Drought 2018 Team"), Nil, Nil), atcCreator),
			titles = Seq(title),
			publisher = "ICOS Carbon Portal",
			publicationYear = 2020,
			resourceType = ResourceType("ASCII Files", ResourceTypeGeneral.Collection),
			subjects = Seq(
				Subject("carbon dioxide")
			),
			contributors = ((atcContrib +: contribs) ++ atcPeople).distinct,
			dates = Seq(
				Date(Instant.now.toString.take(10), DateType.Issued)
			),
			formats = Seq("Collection of ICOS ATC ASCII files"),
			version = Some(Version(1, 0)),
			rights = Seq(cc4by),
			descriptions = Seq(Description(descr, DescriptionType.Abstract, None))
		)
	}

}

object DroughtDoiMaker2{
	import DoiMaker.{atc,etc}

	def creatorPerson(p: PersonEntry) = Creator(
		name = PersonalName(p.firstName, p.lastName),
		nameIds = p.orcid.toSeq.map(oid => NameIdentifier(oid, NameIdentifierScheme.Orcid)),
		affiliations = Seq(p.affiliation.name)
	)

	def dataCollectorPerson(p: PersonEntry) = {
		val cr = creatorPerson(p)
		Contributor(cr.name, cr.nameIds, cr.affiliations, ContributorType.DataCollector)
	}

	def creatorStation(longName: String, stationId: String) = Creator(
		name = GenericName(longName),
		nameIds = Seq(NameIdentifier(stationId, NameIdentifierScheme.Fluxnet)),
		affiliations = Nil
	)

	def contributorStation(longName: String, stationId: String, typ: ContributorType.Value): Contributor = {
		val cr = creatorStation(longName, stationId)
		Contributor(cr.name, cr.nameIds, cr.affiliations, typ)
	}

	private def tcPerson(fname: String, lname: String, typ: ContributorType.Value, tc: GenericName) =
		Contributor(PersonalName(fname, lname), Nil, Seq(tc.name), typ)

	private def atcPerson(fname: String, lname: String, typ: ContributorType.Value) = tcPerson(fname, lname, typ, atc)
	private def etcPerson(fname: String, lname: String, typ: ContributorType.Value) = tcPerson(fname, lname, typ, etc)

	val atcPeople: Seq[Contributor] = Seq(
		atcPerson("Lynn", "Hazan", ContributorType.Producer)
	)

	val etcPeople: Seq[Contributor] = Seq(
		etcPerson("Eleonora", "Canfora", ContributorType.DataCurator),
		etcPerson("Carlo", "Trotta", ContributorType.Producer),
		etcPerson("Dario", "Papale", ContributorType.ProjectLeader)
	)

	val atcCreator = Creator(atc, Nil, Nil)
	val etcCreator = Creator(etc, Nil, Nil)
	val atcContrib = Contributor(atc, Nil, Nil, ContributorType.Producer)
	val etcContrib = Contributor(etc, Nil, Nil, ContributorType.Producer)
}
