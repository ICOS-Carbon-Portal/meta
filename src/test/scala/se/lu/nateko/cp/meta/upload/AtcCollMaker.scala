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

		uploader.fetchDataObject(items.head).map{dobj =>
			for(
				l2 <- dobj.specificInfo;
				doi <- maker.collectionDoi(items)
			){
				val dto = makeDto(l2.acquisition.station, items, doi, prevColLookup)
				println(dto)
				//val doiMeta = makeDoiMeta(dto, dobj)
			}
			Done
		}
	}
}

object AtcCollMaker{

	def makeDoiMeta(dto: StaticCollectionDto, sample: DataObject): DoiMeta = {
		val title = Title(dto.title, None, None)
		???
	}

	def makeDto(station: Station, items: Seq[URI], doi: Doi, prevColLookup: Map[URI, URI]) = StaticCollectionDto(
		submitterId = "CP",
		members = items,
		title = s"ICOS Atmosphere Level 2 data, ${station.name}, release 2020-1",
		description = Some(
			"ICOS Atmospheric Greenhouse Gas Mole Fractions of CO2, CH4, CO, 14C, and Meteorological Observations, " +
			s"period September 2015-June 2020, station ${station.name}, final quality controlled Level 2 data, release 2020-1"
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
