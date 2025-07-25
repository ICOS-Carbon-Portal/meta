package se.lu.nateko.cp.meta.upload

import akka.Done
import se.lu.nateko.cp.doi.*
import se.lu.nateko.cp.doi.meta.*
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{DataObject, Person, Station}
import se.lu.nateko.cp.meta.core.sparql.{BoundUri, SparqlSelectResult}
import se.lu.nateko.cp.meta.services.upload.UploadService
import se.lu.nateko.cp.meta.utils.async.*

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

class AtcCollMaker(maker: DoiMaker, uploader: CpUploadClient)(implicit ctxt: ExecutionContext) {
	import AtcCollMaker.*
	import maker.sparqlHelper.sparql

	def makeColls(): Future[Done] = for(
		stationToColl <- sparql.select(stationCollsQuery("2024-06-25", "2024-06-30")).map(parseStationColls);
		//_ = println(stationToColl);
		stationToItems <- sparql.select(dobjStationQuery("2024-06-15")).map(parseStationObjs);
		count = stationToItems.values.map(_.map.values.map(_.size)).flatten.sum;
		_ = println(s"Total number of data objects: $count");
		done <- executeSequentially(stationToItems){
			(makeStationColl(stationToColl) _).tupled
		}
	) yield done

	def makeStationColl(prevColLookup: Map[URI, URI])(stationUri: URI, items: SpecDobjs): Future[Done] = {
		val sampleUris = items.map.values.map(_.head)
		traverseFut(sampleUris)(uploader.fetchDataObject).flatMap{sampleDobjs =>
			val dobjUris = items.map.values.flatten.toSeq
			val doneFutOpt = for
				l2 <- sampleDobjs.head.specificInfo.toOption;
				station = l2.acquisition.station;
				//if Set("CBW", "IZO") contains station.id;
				hash <- UploadService.collectionHash(dobjUris).toOption;
				prevCol = prevColLookup.get(station.org.self.uri)
			yield
				val doi = maker.client.doi(DoiMaker.coolDoi(hash))
				val dto = makeDto(l2.acquisition.station, dobjUris, doi, prevCol)
				val doiMeta = makeDoiMeta(dto, doi, sampleDobjs).copy(url = Some(s"https://meta.icos-cp.eu/collections/${hash.id}"))
				// println(dto)
				// println(doiMeta)
				// println(s"done for ${station.org.name}")
				// ok
				for
					_ <- uploader.uploadSingleCollMeta(dto);
					_ = println(s"collection created for $stationUri");
					_ <- maker.client.putMetadata(doiMeta)
				yield
					println(s"minted DOI; done for $stationUri")
					Done
			doneFutOpt.getOrElse(ok)
		}
	}
}

object AtcCollMaker{

	class SpecDobjs(val map: Map[URI, Seq[URI]]){
		def this(spec: URI, dobj: URI) = this(Map(spec -> Seq(dobj)))

		def merge(other: SpecDobjs) = new SpecDobjs(
			map.toSeq.concat(other.map).groupMapReduce(_._1)(_._2)(_ :++ _)
		)
	}

	val contributors = Seq(
		"ICOS ATC-Laboratoires Des Sciences Du Climat Et De L'Environnement (LSCE), France" -> ContributorType.DataCurator,
		"ICOS Central Radiocarbon Laboratory (CRL), Germany" -> ContributorType.DataCurator,
		"ICOS ERIC--Carbon Portal, Sweden" -> ContributorType.DataManager,
		"ICOS Flask And Calibration Laboratory (FCL), Germany" -> ContributorType.DataCollector
	).map{
		case (name, cType) => Contributor(GenericName(name), Nil, Nil, Some(cType))
	}

	val icosRiCreator = Creator(GenericName("ICOS RI"), Seq(NameIdentifier("01d0fc168", NameIdentifierScheme.ROR)), Nil)

	def makeDoiMeta(dto: StaticCollectionDto, doi: Doi, samples: Seq[DataObject]): DoiMeta = {
		val creators = samples.flatMap(_.references.authors.getOrElse(Nil)).distinct.collect{ _ match {
			case pers: Person =>
				Creator(
					name = PersonalName(pers.firstName, pers.lastName),
					nameIdentifiers = pers.orcid.map(orc => NameIdentifier(orc.shortId, NameIdentifierScheme.ORCID)).toSeq,
					affiliation = Nil
				)
			}
		}
		val geoPoss = for
			dobj <- samples
			stSpec <- dobj.specificInfo.toOption
			station = stSpec.acquisition.station
			loc <- station.location
		yield loc

		val geoLocs = geoPoss.distinct.map: pos =>
			GeoLocation(
				geoLocationPoint = Some(GeoLocationPoint(
					pointLatitude = Some(Latitude(pos.lat)),
					pointLongitude = Some(Longitude(pos.lon))
				)),
				geoLocationBox = None,
				geoLocationPlace = pos.label
			)

		DoiMeta(
			doi = doi,
			creators = creators :+ icosRiCreator,
			titles = Some(Seq(Title(dto.title, None, None))),
			publisher = Some("ICOS ERIC -- Carbon Portal"),
			publicationYear = Some(2025),
			types = Some(ResourceType(Some("ZIP archives"), Some(ResourceTypeGeneral.Collection))),
			subjects = Seq(
				Subject("Biogeochemical cycles, processes, and modeling"),
				Subject("Troposphere: composition and chemistry")
			),
			contributors = contributors,
			dates = Seq(
				Date(java.time.Instant.now.toString.take(10), Some(DateType.Issued))
			),
			formats = Seq("Collection of ICOS ATC ASCII files"),
			version = Some(Version(1, 0)),
			rightsList = Some(Seq(DoiMaker.ccby4)),
			descriptions = dto.description.map(d => Description(d, DescriptionType.Abstract, None)).toSeq,
			geoLocations = Option(geoLocs).filterNot(_.isEmpty),
			state = DoiPublicationState.findable,
			event = Some(DoiPublicationEvent.publish)
		)
	}

	def makeDto(station: Station, items: Seq[URI], doi: Doi, prevColOpt: Option[URI]) = StaticCollectionDto(
		submitterId = "CP",
		members = items,
		title = s"ICOS Atmosphere Level 2 data, ${station.org.name}, release 2025-1",
		description = Some(
			"ICOS Atmospheric Greenhouse Gas Mole Fractions of CO2, CH4, CO, 14C, N2O, meteorology, " +
			"and flask samples analyzed for CO2, CH4, N2O, CO, H2, SF6, delta O2N2 and 14C" +
			s"period up to March 2025, station ${station.org.name}, final quality controlled Level 2 data, release 2025-1"
		),
		isNextVersionOf = prevColOpt.flatMap(getHashSuff).map(Left(_)),
		preExistingDoi = Some(doi),
		documentation = None,
		coverage = None
	)

	def parseStationObjs(spRes: SparqlSelectResult): Map[URI, SpecDobjs] =
		getUriSeqs(spRes, "station", "spec", "dobj").groupMapReduce(_.apply(0)){
			case Seq(_, spec, dobj) => new SpecDobjs(spec, dobj)
		}(_ merge _)

	def dobjStationQuery(submittedAfter: String) = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix prov: <http://www.w3.org/ns/prov#>
select ?dobj ?station ?spec #?fileName
where {
	VALUES ?spec {
		<http://meta.icos-cp.eu/resources/cpmeta/atcCoL2DataObject>
		<http://meta.icos-cp.eu/resources/cpmeta/atcCh4L2DataObject>
		<http://meta.icos-cp.eu/resources/cpmeta/atcCo2L2DataObject>
		<http://meta.icos-cp.eu/resources/cpmeta/atcMtoL2DataObject>
		<http://meta.icos-cp.eu/resources/cpmeta/atcC14L2DataObject>
		<http://meta.icos-cp.eu/resources/cpmeta/atcN2oL2DataObject>
		<http://meta.icos-cp.eu/resources/cpmeta/atcFlaskDataObject>
	}
	?dobj cpmeta:hasObjectSpec ?spec .
	?dobj cpmeta:hasSizeInBytes ?size .
	#?dobj cpmeta:hasName ?fileName .
	?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
	filter(?submTime > '${submittedAfter}T00:00:00.000Z'^^xsd:dateTime)
	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
}"""

	def parseStationColls(spRes: SparqlSelectResult): Map[URI, URI] =
		getUriSeqs(spRes, "station", "coll")
			.groupBy(_.apply(1)) //group by collection
			.filter(_._2.length == 1) //keep only collections with single station
			.toSeq.flatMap(_._2) //unwrap back to list of pairs
			.map{case Seq(station, coll) => (station, coll)}
			.toMap //to map staion -> collection, with single-station collections only

	def stationCollsQuery(submFrom: String, submTo: String) = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix dcterms: <http://purl.org/dc/terms/>
prefix prov: <http://www.w3.org/ns/prov#>
select distinct ?coll ?station where{
	?coll a cpmeta:Collection .
	filter not exists {[] cpmeta:isNextVersionOf ?coll}
	?coll dcterms:hasPart ?dobj .
	?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
	filter exists{
		?dobj cpmeta:hasObjectSpec ?spec .
		?spec cpmeta:hasDataTheme <http://meta.icos-cp.eu/resources/themes/atmosphere> ;
			cpmeta:hasAssociatedProject <http://meta.icos-cp.eu/resources/projects/icos>;
			cpmeta:hasDataLevel "2"^^xsd:integer .
		?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
		FILTER( ?submTime >= '${submFrom}T00:00:00.000Z'^^xsd:dateTime && ?submTime <= '${submTo}T00:00:00.000Z'^^xsd:dateTime )
	}
}"""

	def getUriSeqs(spRes: SparqlSelectResult, varNames: String*): Seq[Seq[URI]] = spRes
		.results.bindings.flatMap{b =>
			varNames.map(
				vName => b.get(vName).collect{case BoundUri(uri) => Seq(uri)}
			).reduce[Option[Seq[URI]]]{(opSeq1, opSeq2) =>
				for(s1 <- opSeq1; s2 <- opSeq2) yield s1 ++ s2
			}
		}

	def getHashSuff(uri: URI): Option[Sha256Sum] = Sha256Sum.fromBase64Url(uri.toString.split('/').last).toOption
}
