package se.lu.nateko.cp.meta.upload.drought

import se.lu.nateko.cp.doi.core.DoiClient
import se.lu.nateko.cp.doi.*
import se.lu.nateko.cp.doi.meta.*
import scala.concurrent.Future
import se.lu.nateko.cp.meta.core.etcupload.StationId
import java.net.URI
import scala.concurrent.ExecutionContext
import se.lu.nateko.cp.meta.upload.*
import se.lu.nateko.cp.meta.utils.async.executeSequentially
import akka.Done
import java.time.Instant
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.services.citation.CitationClient


class DroughtDoiMaker2(maker: DoiMaker, citer: CitationClient)(implicit ctxt: ExecutionContext){
	import DroughtDoiMaker2.*
	import DoiMaker.*

	def publishDoi(doiMeta: DoiMeta, objHash: Sha256Sum): Future[Doi] = {
		val target = UploadWorkbench.toCpDobj(objHash)
		maker.saveDoi(doiMeta.copy(url = Some(target.toString))).map(_ => doiMeta.doi)
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

		val title = Title(s"Warm winter 2020 ecosystem eddy covariance flux product from " + stationName, None, None)
		val descr = "Public release of the observational data product for eddy covariance fluxes " +
			s"at $stationName, covering the period $yearFrom-$yearTo"
		val majorVersion = 1//if(meta.prevHash.isDefined) 2 else 1

		DoiMeta(
			doi = maker.client.doi(coolDoi(meta.hash)),
			creators = meta.authors.map(creatorPerson) :+ etcCreator,
			state = DoiPublicationState.registered,
			event = Some(DoiPublicationEvent.publish),
			titles = Some(Seq(title)),
			publisher = Some("ICOS Carbon Portal"),
			publicationYear = Some(2022),
			types = Some(ResourceType(Some("FLUXNET zip archive"), Some(ResourceTypeGeneral.Dataset))),
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
			rightsList = Some(Seq(ccby4)),
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
			doi = maker.client.doi(coolDoi(meta.hash)),
			creators = meta.authors.map(creatorPerson) :+ atcCreator,
			titles = Some(Seq(title)),
			publisher = Some("ICOS Carbon Portal"),
			publicationYear = Some(2020),
			types = Some(ResourceType(Some("ICOS ATC time series"), Some(ResourceTypeGeneral.Dataset))),
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
			rightsList = Some(Seq(ccby4)),
			descriptions = Seq(Description(descr, DescriptionType.Abstract, None)) ++ commentOpt.toSeq.map(comm =>
				Description(comm, DescriptionType.Other, None)
			)
		)
	}

	def fluxCollDoiMeta(suffix: String, metas: IndexedSeq[FileEntry]): DoiMeta = {
		val contribs = metas.flatMap(fe => fe.authors ++ fe.contribs).groupBy(_.id).map{
			case (_, pes) => dataCollectorPerson(pes.head)
		}.toSeq
		val title = Title("Warm Winter 2020 ecosystem eddy covariance flux product for 73 stations in FLUXNET-Archive format—release 2022-1", None, None)
		val descr = "This is the release of the observational data product for eddy covariance fluxes at 73 stations in the ecosystem domain, part of them outside the ICOS network, covering the period 1989-2020. The data are in the standard format used for the ICOS L2 ecosystem products and also used by other regional networks like AmeriFlux. The processing has been done using the ONEFlux processing pipeline (https://github.com/icos-etc/ONEFlux) and is fully compliant and integrable with the FLUXNET2015 release (https://fluxnet.fluxdata.org/) and other datasets processed with the same pipeline (AmeriFlux, ICOS L2)."
		DoiMeta(
			doi = maker.client.doi(suffix),
			state = DoiPublicationState.registered,
			event = Some(DoiPublicationEvent.publish),
			creators = Seq(Creator(GenericName("Warm Winter 2020 Team"), Nil, Nil), etcCreator),
			titles = Some(Seq(title)),
			publisher = Some("ICOS Carbon Portal"),
			publicationYear = Some(2020),
			types = Some(ResourceType(Some("ZIP archives"), Some(ResourceTypeGeneral.Collection))),
			subjects = Seq(
				Subject("Biogeochemical cycles, processes, and modeling"),
				Subject("Troposphere: composition and chemistry")
			),
			contributors = ((etcContrib +: contribs) ++ etcPeople).distinct,
			dates = Seq(
				Date(Instant.now.toString.take(10), DateType.Issued)
			),
			formats = Seq("Collection of FLUXNET product ZIP archives"),
			version = Some(Version(1, 0)),
			rightsList = Some(Seq(ccby4)),
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
			doi = maker.client.doi(suffix),
			creators = Seq(Creator(GenericName("Drought 2018 Team"), Nil, Nil), atcCreator),
			titles = Some(Seq(title)),
			publisher = Some("ICOS Carbon Portal"),
			publicationYear = Some(2020),
			types = Some(ResourceType(Some("ASCII Files"), Some(ResourceTypeGeneral.Collection))),
			subjects = Seq(
				Subject("carbon dioxide")
			),
			contributors = ((atcContrib +: contribs) ++ atcPeople).distinct,
			dates = Seq(
				Date(Instant.now.toString.take(10), DateType.Issued)
			),
			formats = Seq("Collection of ICOS ATC ASCII files"),
			version = Some(Version(1, 0)),
			rightsList = Some(Seq(ccby4)),
			descriptions = Seq(Description(descr, DescriptionType.Abstract, None))
		)
	}

}

object DroughtDoiMaker2{
	import DoiMaker.{atc,etc}

	def creatorPerson(p: PersonEntry) = Creator(
		name = PersonalName(p.firstName, p.lastName),
		nameIdentifiers = p.orcid.toSeq.map(oid => NameIdentifier(oid, NameIdentifierScheme.Orcid)),
		affiliation = Seq(p.affiliation.name)
	)

	def dataCollectorPerson(p: PersonEntry) = {
		val cr = creatorPerson(p)
		Contributor(cr.name, cr.nameIdentifiers, cr.affiliation, Some(ContributorType.DataCollector))
	}

	def creatorStation(longName: String, stationId: String) = Creator(
		name = GenericName(longName),
		nameIdentifiers = Seq(NameIdentifier(stationId, NameIdentifierScheme.Fluxnet)),
		affiliation = Nil
	)

	def contributorStation(longName: String, stationId: String, typ: ContributorType.Value): Contributor = {
		val cr = creatorStation(longName, stationId)
		Contributor(cr.name, cr.nameIdentifiers, cr.affiliation, Some(typ))
	}

	private def tcPerson(fname: String, lname: String, typ: ContributorType.Value, tc: GenericName) =
		Contributor(PersonalName(fname, lname), Nil, Seq(tc.name), Some(typ))

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
	val atcContrib = Contributor(atc, Nil, Nil, Some(ContributorType.Producer))
	val etcContrib = Contributor(etc, Nil, Nil, Some(ContributorType.Producer))
}
