package se.lu.nateko.cp.meta.upload

import se.lu.nateko.cp.doi._
import se.lu.nateko.cp.doi.meta._
import se.lu.nateko.cp.meta.core.data.DataObject
import scala.concurrent.ExecutionContext
import se.lu.nateko.cp.meta.core.sparql.SparqlSelectResult
import java.net.URI
import se.lu.nateko.cp.meta.core.sparql.BoundLiteral
import se.lu.nateko.cp.meta.core.sparql.BoundUri
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.core.data.Station
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import scala.concurrent.Future
import akka.Done
import se.lu.nateko.cp.meta.utils.async.executeSequentially
import se.lu.nateko.cp.meta.services.upload.UploadService

class AtcCollMaker(maker: DoiMaker, uploader: CpUploadClient)(implicit ctxt: ExecutionContext) {
	import AtcCollMaker._
	import maker.sparqlHelper.sparql

	def makeColls(): Future[Done] = for(
		stationToColl <- sparql.select(colStationQuery).map(parseStationColls);
		stationToItems <- sparql.select(dobjStationQuery).map(parseStationObjs);
		done <- executeSequentially(stationToItems){
			(makeStationColl(stationToColl) _).tupled
		}
	) yield done

	def makeStationColl(prevColLookup: Map[URI, URI])(station: URI, items: Seq[URI]): Future[Done] = {

		uploader.fetchDataObject(items.head).flatMap{dobj =>
			val doneFutOpt = for(
				l2 <- dobj.specificInfo.toOption;
				hash <- UploadService.collectionHash(items).toOption
			) yield {
				val doi = maker.client.doi(DoiMaker.coolDoi(hash))
				val dto = makeDto(l2.acquisition.station, items, doi, prevColLookup)
				val doiMeta = makeDoiMeta(dto, doi, dobj)
				for(
					// _ <- uploader.uploadSingleCollMeta(dto);
					// _ = println(s"collection created for $station");
					_ <- maker.client.setDoi(doiMeta, new java.net.URL(s"https://meta.icos-cp.eu/collections/${hash.id}"))
				) yield {
					println(s"done for $station")
					Done
				}
			}
			doneFutOpt.getOrElse(Future.successful(Done))
		}
	}
}

object AtcCollMaker{

	val contributors = Seq(
		"ICOS ATC-Laboratoires Des Sciences Du Climat Et De L'Environnement (LSCE), France" -> ContributorType.DataCurator,
		"ICOS Central Radiocarbon Laboratory (CRL), Germany" -> ContributorType.DataCurator,
		"ICOS ERIC--Carbon Portal, Sweden" -> ContributorType.DataManager,
		"ICOS Flask And Calibration Laboratory (FCL), Germany" -> ContributorType.DataCollector
	).map{
		case (name, cType) => Contributor(GenericName(name), Nil, Nil, cType)
	}

	val lampedusaCreators = Seq(
		"Alcide" -> "di Sarra",
		"Salvatore" -> "Piacentino",
		"Damiano" -> "Sferlazzo"
	).map{
		case (fname, lname) => Creator(PersonalName(fname, lname), Nil, Seq("ENEA"))
	}

	def makeDoiMeta(dto: StaticCollectionDto, doi: Doi, sample: DataObject): DoiMeta = {
		val creators = sample.references.authors.getOrElse(Nil).map{pers =>
			Creator(
				name = PersonalName(pers.firstName, pers.lastName),
				nameIds = pers.orcid.map(orc => NameIdentifier(orc.shortId, NameIdentifierScheme.Orcid)).toSeq,
				affiliations = Nil
			)
		}
		DoiMeta(
			id = doi,
			creators = if(creators.isEmpty && dto.title.contains("Lampedusa")) lampedusaCreators else creators,
			titles = Seq(Title(dto.title, None, None)),
			publisher = "ICOS ERIC -- Carbon Portal",
			publicationYear = 2020,
			resourceType = ResourceType("ZIP archives", ResourceTypeGeneral.Collection),
			subjects = Seq(
				Subject("Biogeochemical cycles, processes, and modeling"),
				Subject("Troposphere: composition and chemistry")
			),
			contributors = contributors,
			dates = Seq(
				Date(java.time.Instant.now.toString.take(10), DateType.Issued)
			),
			formats = Seq("Collection of ICOS ATC ASCII files"),
			version = Some(Version(1, 0)),
			rights = Seq(DoiMaker.cc4by),
			descriptions = dto.description.map(d => Description(d, DescriptionType.Abstract, None)).toSeq
		)
	}

	def makeDto(station: Station, items: Seq[URI], doi: Doi, prevColLookup: Map[URI, URI]) = StaticCollectionDto(
		submitterId = "CP",
		members = items,
		title = s"ICOS Atmosphere Level 2 data, ${station.org.name}, release 2020-1",
		description = Some(
			"ICOS Atmospheric Greenhouse Gas Mole Fractions of CO2, CH4, CO, 14C, and Meteorological Observations, " +
			s"period September 2015-June 2020, station ${station.org.name}, final quality controlled Level 2 data, release 2020-1"
		),
		isNextVersionOf = prevColLookup.get(station.org.self.uri).flatMap(getHashSuff).map(Left(_)),
		preExistingDoi = Some(doi)
	)

	def parseStationObjs(spRes: SparqlSelectResult): Map[URI, Seq[URI]] = 
		getUriPairs(spRes, "station", "dobj").groupMap(_._1)(_._2)

	val dobjStationQuery = """prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix prov: <http://www.w3.org/ns/prov#>
select ?dobj ?station #?fileName
where {
	VALUES ?spec {<http://meta.icos-cp.eu/resources/cpmeta/atcCoL2DataObject> <http://meta.icos-cp.eu/resources/cpmeta/atcCh4L2DataObject> <http://meta.icos-cp.eu/resources/cpmeta/atcCo2L2DataObject> <http://meta.icos-cp.eu/resources/cpmeta/atcMtoL2DataObject> <http://meta.icos-cp.eu/resources/cpmeta/atcC14L2DataObject>}
	?dobj cpmeta:hasObjectSpec ?spec .
	?dobj cpmeta:hasSizeInBytes ?size .
	#?dobj cpmeta:hasName ?fileName .
	?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
}
order by ?stationName"""

	def parseStationColls(spRes: SparqlSelectResult): Map[URI, URI] = 
		getUriPairs(spRes, "station", "coll")
			.groupBy(_._2) //group by collection
			.filter(_._2.length == 1) //keep only collections with single station
			.toSeq.flatMap(_._2) //unwrap back to list of pairs
			.toMap //to map staion -> collection, with single-station collections only

	val colStationQuery = """prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix dcterms: <http://purl.org/dc/terms/>
prefix prov: <http://www.w3.org/ns/prov#>
select distinct ?coll ?station where{
	?coll a cpmeta:Collection .
	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?coll}
	?coll dcterms:hasPart ?dobj .
	?dobj cpmeta:hasObjectSpec [
		cpmeta:hasDataTheme <http://meta.icos-cp.eu/resources/themes/atmosphere> ;
		cpmeta:hasAssociatedProject <http://meta.icos-cp.eu/resources/projects/icos>;
		cpmeta:hasDataLevel "2"^^xsd:integer
	] .
	?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
}"""

	def getUriPairs(spRes: SparqlSelectResult, var1: String, var2: String): Seq[(URI, URI)] = spRes
		.results.bindings.flatMap{b =>
			for(
				uri1 <- b.get(var1).collect{case BoundUri(uri) => uri};
				uri2 <- b.get(var2).collect{case BoundUri(uri) => uri}
			) yield uri1 -> uri2
		}

	def getHashSuff(uri: URI): Option[Sha256Sum] = Sha256Sum.fromBase64Url(uri.toString.split('/').last).toOption
}
