package se.lu.nateko.cp.meta.upload.drought
import akka.Done
import se.lu.nateko.cp.doi.*
import se.lu.nateko.cp.doi.meta.*
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.upload.*
import se.lu.nateko.cp.meta.utils.async.executeSequentially

import java.net.URI
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}


class DroughtDoiMaker(maker: DoiMaker, peeps: Map[URI, PersonalName], names: Map[URI, String])(implicit ctxt: ExecutionContext){
	import DroughtDoiMaker.*
	import DoiMaker.*

	def publishDoi(meta: FluxMeta): Future[Doi] = {
		val doiMeta = makeDoiMeta(meta)
		maker.saveDoi(doiMeta).map(_ => doiMeta.doi)
	}

	def publishDois(metas: Seq[FluxMeta]): Future[Done] = executeSequentially(metas){meta =>
		publishDoi(meta).map{doi =>
			println(s"Success for ${meta.stationId.id}: $doi")
			Done
		}
	}

	def publishCollDoi(suffix: String, target: URI): Future[Done] =
		maker.saveDoi(collDoiMeta(suffix).copy(url = Some(target.toString)))

	def makeDoiMeta(meta: FluxMeta): DoiMeta = {
		val stationName = names(meta.station)
		val title = Title(s"Drought-2018 ecosystem eddy covariance flux product from " + stationName, None, None)
		val descr = "Public release of the observational data product for eddy covariance fluxes " +
			s"at $stationName, covering the period ${meta.yearFrom}-${meta.yearTo}"
		val minorVersion = if(meta.prevVers.isDefined) 1 else 0

		DoiMeta(
			doi = maker.client.doi(coolDoi(meta.hash)),
			creators = Seq(etcCreator, creatorStation(stationName, meta.stationId)),
			titles = Some(Seq(title)),
			publisher = Some("ICOS Carbon Portal"),
			publicationYear = Some(2019),
			types = Some(ResourceType(Some("FLUXNET zip archive"), Some(ResourceTypeGeneral.Dataset))),
			subjects = Seq(
				Subject("Biogeochemical cycles, processes, and modeling"),
				Subject("Troposphere: composition and chemistry")
			),
			contributors = Seq(
				etcContrib,
				contributorStation(stationName, meta.stationId, ContributorType.DataCollector),
				contributorPi(meta.pi, Seq(meta.station))
			) ++ etcPeople,
			dates = Seq(
				Date(meta.creationDate.toString.take(10), Some(DateType.Created))
			),
			formats = Seq("ZIP archive with ASCII CSV files"),
			version = Some(Version(1, minorVersion)),
			rightsList = Some(Seq(ccby4)),
			descriptions = Seq(Description(descr, DescriptionType.Abstract, None)) ++ meta.comment.toSeq.map(comm =>
				Description(comm, DescriptionType.Other, None)
			),
			url = Some(UploadWorkbench.toCpDobj(meta.hash).toString)
		)
	}

	def collDoiMeta(suffix: String): DoiMeta = {
		val metas = DroughtUpload.archiveMetas.toIndexedSeq
		val piContribs = metas.groupBy(_.pi).map{
			case (pi, metas) => contributorPi(pi, metas.map(_.station))
		}.toSeq
		val title = Title("Drought-2018 ecosystem eddy covariance flux product in FLUXNET-Archive format - release 2019-1", None, None)
		val descr = s"This is the first public release of the observational data product for eddy covariance fluxes at ${metas.size} stations " +
			"in the ecosystem domain from the Drought-2018 team, covering the period 1989-2018."
		DoiMeta(
			doi = maker.client.doi(suffix),
			creators = Seq(Creator(GenericName("Drought 2018 Team"), Nil, Nil), etcCreator),
			titles = Some(Seq(title)),
			publisher = Some("ICOS Carbon Portal"),
			publicationYear = Some(2019),
			types = Some(ResourceType(Some("ZIP archives"), Some(ResourceTypeGeneral.Collection))),
			subjects = Seq(
				Subject("Biogeochemical cycles, processes, and modeling"),
				Subject("Troposphere: composition and chemistry")
			),
			contributors = ???,//((etcContrib +: piContribs) ++ etcPeople).distinct,
			dates = Seq(
				Date(Instant.now.toString.take(10), Some(DateType.Issued))
			),
			formats = Seq("Collection of FLUXNET product ZIP archives"),
			version = Some(Version(1, 1)),
			rightsList = Some(Seq(ccby4)),
			descriptions = Seq(Description(descr, DescriptionType.Abstract, None))
		)
	}

	def contributorPi(piId: URI, stIds: Seq[URI]) = ???//Contributor(
		//peeps(piId), Nil, stIds.map(stId => "Principal investigator at " + names(stId)), Some(ContributorType.DataCollector)
	//)
}

object DroughtDoiMaker{
	import DoiMaker.etc

	def apply(doiMaker: DoiMaker)(implicit ctxt: ExecutionContext): Future[DroughtDoiMaker] = {
		val metas = DroughtUpload.archiveMetas.toIndexedSeq
		val peepUris = metas.map(_.pi).distinct
		val stationUris = metas.map(_.station).distinct
		val sparql = doiMaker.sparqlHelper
		for(
			peeps <- sparql.lookupPeopleNames(peepUris);
			names <- sparql.lookupCpNames(stationUris)
		) yield new DroughtDoiMaker(doiMaker, peeps, names)
	}

	def creatorStation(longName: String, id: StationId) = Creator(
		name = GenericName(longName),
		nameIdentifiers = Seq(NameIdentifier(id.id, NameIdentifierScheme.FLUXNET)),
		affiliation = Nil
	)

	def contributorStation(longName: String, id: StationId, typ: ContributorType): Contributor = {
		val cr = creatorStation(longName, id)
		Contributor(cr.name, cr.nameIdentifiers, cr.affiliation, Some(typ))
	}

	private def etcPerson(fname: String, lname: String, typ: ContributorType) = ???
		//Contributor(PersonalName(fname, lname), Nil, Seq(etc.name), Some(typ))

	val etcPeople: Seq[Contributor] = Seq(
		etcPerson("Eleonora", "Canfora", ContributorType.DataCurator),
		etcPerson("Carlo", "Trotta", ContributorType.Producer),
		etcPerson("Dario", "Papale", ContributorType.ProjectLeader)
	)

	val etcCreator = Creator(etc, Nil, Nil)
	val etcContrib = Contributor(etc, Nil, Nil, Some(ContributorType.Producer))
}
