package se.lu.nateko.cp.meta.services.upload

import java.net.URI

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j._

import scala.util.Try

trait CpmetaFetcher extends FetchingHelper{
	protected final lazy val metaVocab = new CpmetaVocab(server.factory)
	protected def vocab: CpVocab

	def getSpecification(spec: IRI, fetcher: PlainStaticObjectFetcher) = DataObjectSpec(
		self = getLabeledResource(spec),
		project = getLabeledResource(spec, metaVocab.hasAssociatedProject),
		theme = getDataTheme(getSingleUri(spec, metaVocab.hasDataTheme)),
		format = getLabeledResource(spec, metaVocab.hasFormat),
		encoding = getLabeledResource(spec, metaVocab.hasEncoding),
		dataLevel = getSingleInt(spec, metaVocab.hasDataLevel),
		datasetSpec = getOptionalUri(spec, metaVocab.containsDataset).map{iri =>
				import se.lu.nateko.cp.meta.core.data.JsonSupport.uriResourceFormat
				import spray.json._
				getLabeledResource(iri).toJson
			},
		documentation = server.getUriValues(spec, metaVocab.hasDocumentationObject).map(fetcher.getPlainStaticObject),
		description = server.getStringValues(spec, RDFS.COMMENT)
	)

	def getOptionalSpecificationFormat(spec: IRI): Option[IRI] = getOptionalUri(spec, metaVocab.hasFormat)

	protected def getLatLonBox(cov: IRI) = LatLonBox(
		min = Position(
			lat = getSingleDouble(cov, metaVocab.hasSouthernBound),
			lon = getSingleDouble(cov, metaVocab.hasWesternBound),
			Option.empty
		),
		max = Position(
			lat = getSingleDouble(cov, metaVocab.hasNothernBound),
			lon = getSingleDouble(cov, metaVocab.hasEasternBound),
			Option.empty
		),
		label = getOptionalString(cov, RDFS.LABEL)
	)

	protected def getDataProduction(obj: IRI, prod: IRI, fetcher: PlainStaticObjectFetcher) = DataProduction(
		creator = getAgent(getSingleUri(prod, metaVocab.wasPerformedBy)),
		contributors = server.getUriValues(prod, metaVocab.wasParticipatedInBy).map(getAgent),
		host = getOptionalUri(prod, metaVocab.wasHostedBy).map(getOrganization),
		comment = getOptionalString(prod, RDFS.COMMENT),
		sources = server.getUriValues(obj, metaVocab.prov.hadPrimarySource).map(fetcher.getPlainStaticObject).map(_.asUriResource),
		dateTime = getSingleInstant(prod, metaVocab.hasEndTime)
	)

	protected def getSubmission(subm: IRI): DataSubmission = {
		val submitter: IRI = getSingleUri(subm, metaVocab.prov.wasAssociatedWith)
		DataSubmission(
			submitter = getOrganization(submitter),
			start = getSingleInstant(subm, metaVocab.prov.startedAtTime),
			stop = getOptionalInstant(subm, metaVocab.prov.endedAtTime)
		)
	}

	private def getAgent(uri: IRI): Agent = {
		if(getOptionalString(uri, metaVocab.hasFirstName).isDefined)
			getPerson(uri)
		else getOrganization(uri)
	}

	protected def getOrganization(org: IRI) = Organization(
		self = getLabeledResource(org),
		name = getSingleString(org, metaVocab.hasName)
	)

	protected def getPerson(pers: IRI) = Person(
		self = getLabeledResource(pers),
		firstName = getSingleString(pers, metaVocab.hasFirstName),
		lastName = getSingleString(pers, metaVocab.hasLastName)
	)

	private def getDataTheme(theme: IRI) = DataTheme(
		self = getLabeledResource(theme),
		icon = getSingleUriLiteral(theme, metaVocab.hasIcon),
		markerIcon = getOptionalUriLiteral(theme, metaVocab.hasMarkerIcon)
	)

	private def getTemporalCoverage(dobj: IRI) = TemporalCoverage(
		interval = TimeInterval(
			start = getSingleInstant(dobj, metaVocab.hasStartTime),
			stop = getSingleInstant(dobj, metaVocab.hasEndTime)
		),
		resolution = getOptionalString(dobj, metaVocab.hasTemporalResolution)
	)

	private def getStation(stat: IRI) = Station(
		org = getOrganization(stat),
		id = getOptionalString(stat, metaVocab.hasStationId).getOrElse("Unknown"),
		name = getOptionalString(stat, metaVocab.hasName).getOrElse("Unknown"),
		//TODO: Add support for geoJson from station info (OTC)
		coverage = for(
			posLat <- getOptionalDouble(stat, metaVocab.hasLatitude);
			posLon <- getOptionalDouble(stat, metaVocab.hasLongitude)
		) yield Position(posLat, posLon, getOptionalFloat(stat, metaVocab.hasElevation)),
		responsibleOrganization = getOptionalUri(stat, metaVocab.hasResponsibleOrganization).map(getOrganization)
	)

	def getOptionalStation(station: IRI): Option[Station] = Try(getStation(station)).toOption

	private def getLocation(location: IRI) = Location(
		geometry = getCoverage(location),
		label = getOptionalString(location, RDFS.LABEL)
	)

	private def getSite(site: IRI) = Site(
		self = getLabeledResource(site),
		ecosystem = getLabeledResource(site, metaVocab.hasEcosystemType),
		location = getOptionalUri(site, metaVocab.hasSpatialCoverage).map(getLocation)
	)

	protected def getL3Meta(dobj: IRI, prodOpt: Option[DataProduction]): L3SpecificMeta = {

		val cov = getSingleUri(dobj, metaVocab.hasSpatialCoverage)
		assert(prodOpt.isDefined, "Production info must be provided for a spatial data object")
		val prod = prodOpt.get

		L3SpecificMeta(
			title = getSingleString(dobj, metaVocab.dcterms.title),
			description = getOptionalString(dobj, metaVocab.dcterms.description),
			spatial = getLatLonBox(cov),
			temporal = getTemporalCoverage(dobj),
			productionInfo = prod
		)
	}

	protected def getL2Meta(dobj: IRI, prod: Option[DataProduction]): L2OrLessSpecificMeta = {
		val acqUri = getSingleUri(dobj, metaVocab.wasAcquiredBy)

		val acq = DataAcquisition(
			station = getStation(getSingleUri(acqUri, metaVocab.prov.wasAssociatedWith)),
			site = getOptionalUri(acqUri, metaVocab.wasPerformedAt).map(getSite),
			interval = for(
				start <- getOptionalInstant(acqUri, metaVocab.prov.startedAtTime);
				stop <- getOptionalInstant(acqUri, metaVocab.prov.endedAtTime)
			) yield TimeInterval(start, stop),
			instrument = server.getUriValues(acqUri, metaVocab.wasPerformedWith).map(_.toJava).toList match{
				case Nil => None
				case single :: Nil => Some(Left(single))
				case many => Some(Right(many))
			},
			samplingHeight = getOptionalFloat(acqUri, metaVocab.hasSamplingHeight)
		)
		val nRows = getOptionalInt(dobj, metaVocab.hasNumberOfRows)

		val coverage = getOptionalUri(dobj, metaVocab.hasSpatialCoverage).map(getCoverage)

		L2OrLessSpecificMeta(acq, prod, nRows, coverage)
	}

	private def getCoverage(covUri: IRI): GeoFeature = {
		val covClass = getSingleUri(covUri, RDF.TYPE)

		if(covClass === metaVocab.latLonBoxClass)
			getLatLonBox(covUri)
		else
			GenericGeoFeature(getSingleString(covUri, metaVocab.asGeoJSON))
	}

	protected def getNextVersion(item: IRI): Option[URI] = {
		server.getStatements(None, Some(metaVocab.isNextVersionOf), Some(item))
			.toSeq.headOption.collect{
				case Rdf4jStatement(next, _, _) => next.toJava
			}
	}

	protected def getPreviousVersion(item: IRI): Option[Either[URI, Seq[URI]]] =
		server.getUriValues(item, metaVocab.isNextVersionOf).map(_.toJava).toList match {
			case Nil => None
			case single :: Nil => Some(Left(single))
			case many => Some(Right(many))
		}

	protected def getPreviousVersions(item: IRI): Seq[URI] = getPreviousVersion(item).fold[Seq[URI]](Nil)(_.fold(Seq(_), identity))

}
