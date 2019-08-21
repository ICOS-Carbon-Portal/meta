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


class DroughtDoiMaker(maker: DoiMaker, peeps: Map[URI, PersonalName], names: Map[URI, String])(implicit ctxt: ExecutionContext){
	import DroughtDoiMaker._
	import DoiMaker._

	def publishDoi(meta: FluxMeta): Future[Doi] = {
		val doiMeta = makeDoiMeta(meta)
		val target = UploadWorkbench.toCpDobj(meta.hash)
		maker.setDoi(doiMeta -> target).map(_ => doiMeta.id)
	}

	def publishDois(metas: Seq[FluxMeta]): Future[Done] = executeSequentially(metas){meta =>
		publishDoi(meta).map{doi =>
			println(s"Success for ${meta.stationId.id}: $doi")
			Done
		}
	}

	def publishCollDoi(suffix: String, target: URI): Future[Done] =
		maker.setDoi(collDoiMeta(suffix) -> target)

	def makeDoiMeta(meta: FluxMeta): DoiMeta = {
		val stationName = names(meta.station)
		val title = Title(s"Drought-2018 ecosystem eddy covariance flux product from " + stationName, None, None)
		val descr = "Public release of the observational data product for eddy covariance fluxes " +
			s"at $stationName, covering the period ${meta.yearFrom}-${meta.yearTo}"
		val minorVersion = if(meta.prevVers.isDefined) 1 else 0

		DoiMeta(
			id = maker.client.doi(coolDoi(meta.hash)),
			creators = Seq(etcCreator, creatorStation(stationName, meta.stationId)),
			titles = Seq(title),
			publisher = "ICOS Carbon Portal",
			publicationYear = 2019,
			resourceType = ResourceType("FLUXNET zip archive", ResourceTypeGeneral.Dataset),
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
				Date(meta.creationDate.toString.take(10), DateType.Created)
			),
			formats = Seq("ZIP archive with ASCII CSV files"),
			version = Some(Version(1, minorVersion)),
			rights = Seq(cc4by),
			descriptions = Seq(Description(descr, DescriptionType.Abstract, None)) ++ meta.comment.toSeq.map(comm =>
				Description(comm, DescriptionType.Other, None)
			)
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
			id = maker.client.doi(suffix),
			creators = Seq(Creator(GenericName("Drought 2018 Team"), Nil, Nil), etcCreator),
			titles = Seq(title),
			publisher = "ICOS Carbon Portal",
			publicationYear = 2019,
			resourceType = ResourceType("ZIP archives", ResourceTypeGeneral.Collection),
			subjects = Seq(
				Subject("Biogeochemical cycles, processes, and modeling"),
				Subject("Troposphere: composition and chemistry")
			),
			contributors = ((etcContrib +: piContribs) ++ etcPeople).distinct,
			dates = Seq(
				Date(Instant.now.toString.take(10), DateType.Issued)
			),
			formats = Seq("Collection of FLUXNET product ZIP archives"),
			version = Some(Version(1, 1)),
			rights = Seq(cc4by),
			descriptions = Seq(Description(descr, DescriptionType.Abstract, None))
		)
	}

	def contributorPi(piId: URI, stIds: Seq[URI]) = Contributor(
		peeps(piId), Nil, stIds.map(stId => "Principal investigator at " + names(stId)), ContributorType.DataCollector
	)
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
		nameIds = Seq(NameIdentifier(id.id, NameIdentifierScheme.Fluxnet)),
		affiliations = Nil
	)

	def contributorStation(longName: String, id: StationId, typ: ContributorType.Value): Contributor = {
		val cr = creatorStation(longName, id)
		Contributor(cr.name, cr.nameIds, cr.affiliations, typ)
	}

	private def etcPerson(fname: String, lname: String, typ: ContributorType.Value) =
		Contributor(PersonalName(fname, lname), Nil, Seq(etc.name), typ)

	val etcPeople: Seq[Contributor] = Seq(
		etcPerson("Eleonora", "Canfora", ContributorType.DataCurator),
		etcPerson("Carlo", "Trotta", ContributorType.Producer),
		etcPerson("Dario", "Papale", ContributorType.ProjectLeader)
	)

	val etcCreator = Creator(etc, Nil, Nil)
	val etcContrib = Contributor(etc, Nil, Nil, ContributorType.Producer)
}
