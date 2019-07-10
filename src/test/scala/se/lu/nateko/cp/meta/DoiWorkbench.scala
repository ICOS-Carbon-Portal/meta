package se.lu.nateko.cp.meta
import se.lu.nateko.cp.doi.core.DoiClient
import se.lu.nateko.cp.doi.core.DoiClientConfig
import java.net.URL
import se.lu.nateko.cp.doi.core.PlainJavaDoiHttp
import se.lu.nateko.cp.doi._
import se.lu.nateko.cp.doi.meta._
import scala.concurrent.Future
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.test.utils.SparqlClient
import se.lu.nateko.cp.meta.core.etcupload.StationId
import java.net.URI
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext

object DoiWorkbench{

	var doiMaker: DroughtDoiMaker = null
	val metas = Drought2018.metas.toIndexedSeq
	implicit val system = ActorSystem("doi_workbench")
	import system.dispatcher

	def init(password: String): Unit = {
		val conf = DoiClientConfig("SND.ICOS", password, new URL("https://mds.datacite.org/"), "10.18160")
		val http = new PlainJavaDoiHttp(conf.symbol, password)
		val doiClient = new DoiClient(conf, http)
		val doiMakerFut = DroughtDoiMaker(new URI("https://meta.icos-cp.eu/sparql"), doiClient, metas)
		doiMakerFut.onComplete{
			case Success(value) => doiMaker = value; println("DroughtDoiMaker initialized")
			case Failure(exception) => exception.printStackTrace()
		}
	}

	def coolDoi(hash: Sha256Sum): String = {
		val id: Long = hash.getBytes.take(8).foldLeft(0L){(acc, b) => (acc << 8) + b}
		CoolDoi.makeRandom(id)
	}

	val cc4by = Rights("CC4.0BY", Some("https://creativecommons.org/licenses/by/4.0"))
	val etc = GenericName("ICOS Ecosystem Thematic Centre")
}

class DroughtDoiMaker(client: DoiClient, peeps: Map[URI, PersonalName], names: Map[URI, String]){
	import DroughtDoiMaker._
	import DoiWorkbench._
	import Drought2018.FluxMeta

	def publishDoi(meta: FluxMeta)(implicit ctxt: ExecutionContext): Future[Doi] = {
		val doiMeta = makeDoiMeta(meta)
		val target = UploadWorkbench.toCpDobj(meta.hash)
		client.setDoi(doiMeta, target.toURL).map(_ => doiMeta.id)
	}

	def publishDois(metas: Seq[FluxMeta])(implicit ctxt: ExecutionContext): Future[Unit] = metas.foldLeft(Future.successful(())){(acc, meta) =>
		acc.flatMap{_ =>
			publishDoi(meta).map{doi =>
				println(s"Success for ${meta.stationId.id}: $doi")
			}
		}
	}

	def makeDoiMeta(meta: FluxMeta): DoiMeta = {
		val stationName = names(meta.station)
		val title = Title(s"Drought-2018 ecosystem eddy covariance flux product from " + stationName, None, None)
		val descr = "Public release of the observational data product for eddy covariance fluxes " +
		s"at $stationName, covering the period ${meta.yearFrom}-${meta.yearTo}"

		DoiMeta(
			id = client.doi(coolDoi(meta.hash)),
			creators = Seq(Creator(etc, Nil, Nil), creatorStation(stationName, meta.stationId)),
			titles = Seq(title),
			publisher = "ICOS Carbon Portal",
			publicationYear = 2019,
			resourceType = ResourceType("FLUXNET zip archive", ResourceTypeGeneral.Dataset),
			subjects = Seq(
				Subject("Biogeochemical cycles, processes, and modeling"),
				Subject("Troposphere: composition and chemistry")
			),
			contributors = Seq(
				Contributor(etc, Nil, Nil, ContributorType.Producer),
				contributorStation(stationName, meta.stationId, ContributorType.DataCollector),
				Contributor(peeps(meta.pi), Nil, Seq("Principal investigator at " + stationName), ContributorType.DataCollector)
			) ++ etcPeople,
			dates = Seq(
				Date(meta.creationDate.toString.take(10), DateType.Created)
			),
			formats = Seq("ZIP archive with ASCII CSV files"),
			version = Some(Version(1, 0)),
			rights = Seq(cc4by),
			descriptions = Seq(Description(descr, DescriptionType.Abstract, None)) ++ meta.comment.toSeq.map(comm =>
				Description(comm, DescriptionType.Other, None)
			)
		)
	}
}

object DroughtDoiMaker{
	import DoiWorkbench.etc

	def apply(sparqlEndpoint: URI, client: DoiClient, metas: Seq[Drought2018.FluxMeta])(implicit system: ActorSystem): Future[DroughtDoiMaker] = {
		import system.dispatcher
		val peepUris = metas.map(_.pi).distinct
		val stationUris = metas.map(_.station).distinct
		val sparql = new SparqlHelper(sparqlEndpoint)
		for(
			peeps <- sparql.lookupPeopleNames(peepUris);
			names <- sparql.lookupCpNames(stationUris)
		) yield new DroughtDoiMaker(client, peeps, names)
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
}

class SparqlHelper(endpoint: URI)(implicit system: ActorSystem){
	import se.lu.nateko.cp.meta.core.sparql._
	import system.dispatcher
	val sparql = new SparqlClient(endpoint)

	def lookupPeopleNames(ids: Seq[URI]): Future[Map[URI, PersonalName]] = {
		val query = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
			select * where{
			values ?p {<${ids.mkString("> <")}>}
			?p cpmeta:hasFirstName ?fname ; cpmeta:hasLastName ?lname .
		}"""
		sparql.select(query).map{qr =>
			qr.results.bindings.map{b =>
				val BoundUri(p) = b("p")
				val BoundLiteral(fname, _) = b("fname")
				val BoundLiteral(lname, _) = b("lname")
				p -> PersonalName(fname, lname)
			}.toMap
		}
	}

	def lookupCpNames(ids: Seq[URI]): Future[Map[URI, String]] = {
		val query = s"""select * where{
			values ?id {<${ids.mkString("> <")}>}
			?id <http://meta.icos-cp.eu/ontologies/cpmeta/hasName> ?name
		}"""
		sparql.select(query).map{qr =>
			qr.results.bindings.map{b =>
				val BoundUri(id) = b("id")
				val BoundLiteral(name, _) = b("name")
				id -> name
			}.toMap
		}
	}
}
