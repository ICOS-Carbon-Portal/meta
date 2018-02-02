package se.lu.nateko.cp.meta.services.upload

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS

import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j._

import DataTheme._
import OrganizationClass.OrganizationClass
import se.lu.nateko.cp.meta.services.CpVocab

trait CpmetaFetcher extends FetchingHelper{
	protected def metaVocab: CpmetaVocab
	protected def vocab: CpVocab

	def getPlainDataObject(dobj: IRI) = PlainDataObject(dobj.toJava, getSingleString(dobj, metaVocab.hasName))

	def getSpecification(spec: IRI) = DataObjectSpec(
		self = getLabeledResource(spec),
		format = getLabeledResource(spec, metaVocab.hasFormat),
		encoding = getLabeledResource(spec, metaVocab.hasEncoding),
		dataLevel = getSingleInt(spec, metaVocab.hasDataLevel),
		datasetSpec = None
	)

	def getOptionalSpecificationFormat(spec: IRI): Option[IRI] = getOptionalUri(spec, metaVocab.hasFormat)

	protected def getLatLonBox(cov: IRI) = LatLonBox(
		min = Position(
			lat = getSingleDouble(cov, metaVocab.hasSouthernBound),
			lon = getSingleDouble(cov, metaVocab.hasWesternBound)
		),
		max = Position(
			lat = getSingleDouble(cov, metaVocab.hasNothernBound),
			lon = getSingleDouble(cov, metaVocab.hasEasternBound)
		),
		label = getOptionalString(cov, RDFS.LABEL)
	)

	protected def getDataProduction(prod: IRI) = DataProduction(
		creator = getAgent(getSingleUri(prod, metaVocab.wasPerformedBy)),
		contributors = server.getUriValues(prod, metaVocab.wasParticipatedInBy).map(getAgent),
		host = getOptionalUri(prod, metaVocab.wasHostedBy).map(getOrganization),
		comment = getOptionalString(prod, RDFS.COMMENT),
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
		name = getSingleString(org, metaVocab.hasName),
		orgClass = getOrgClass(org)
	)

	private def getPerson(pers: IRI) = Person(
		self = getLabeledResource(pers),
		firstName = getSingleString(pers, metaVocab.hasFirstName),
		lastName = getSingleString(pers, metaVocab.hasLastName)
	)

	private def getOrgClass(subj: IRI): OrganizationClass = {
		val vocab = metaVocab
		import vocab.{ atmoStationClass, cfClass, ecoStationClass, oceStationClass, orgClass, tcClass, stationClass }
		import OrganizationClass._

		val themes = server.getValues(subj, RDF.TYPE).collect{
			case `atmoStationClass` => AS
			case `ecoStationClass` => ES
			case `oceStationClass` => OS
			case `tcClass` => TC
			case `cfClass` => CF
			case `stationClass` => Station
			case `orgClass` => Org
		}
		themes.headOption.getOrElse(Org)
	}

	private def getDataTheme(subj: IRI): DataTheme = {
		val metavocab = metaVocab
		val cpvocab = vocab
		import metavocab.{ atmoStationClass, cfClass, ecoStationClass, oceStationClass, orgClass, tcClass }
		import cpvocab.{ atc, cal, cp, etc, otc }

		val themes = server.getValues(subj, RDF.TYPE).collect{
			case `atmoStationClass` => Atmosphere
			case `ecoStationClass` => Ecosystem
			case `oceStationClass` => Ocean
			case `tcClass` => subj match{
				case `atc` => Atmosphere
				case `etc` => Ecosystem
				case `otc` => Ocean
			}
			case `cfClass` => subj match{
				case `cp` => CP
				case `cal` => CAL
			}
			case `orgClass` => Other
		}
		themes.headOption.getOrElse(Other)
	}

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
		theme = getDataTheme(stat),
		//TODO: Add support for geoJson from station info (OTC)
		coverage = for(
			posLat <- getOptionalDouble(stat, metaVocab.hasLatitude);
			posLon <- getOptionalDouble(stat, metaVocab.hasLongitude)
		) yield Position(posLat, posLon)
	)

	protected def getL3Meta(dobj: IRI, prodOpt: Option[DataProduction]): L3SpecificMeta = {
		implicit val factory = metaVocab.factory

		val cov = getSingleUri(dobj, metaVocab.hasSpatialCoverage)
		assert(prodOpt.isDefined, "Production info must be provided for a spatial data object")
		val prod = prodOpt.get

		L3SpecificMeta(
			title = getSingleString(dobj, metaVocab.dcterms.title),
			description = getOptionalString(dobj, metaVocab.dcterms.description),
			spatial = getLatLonBox(cov),
			temporal = getTemporalCoverage(dobj),
			productionInfo = prod,
			theme = getDataTheme(prod.host.map(_.self).getOrElse(prod.creator.self).uri.toRdf)
		)
	}

	protected def getL2Meta(dobj: IRI, prod: Option[DataProduction]): L2OrLessSpecificMeta = {
		val acqUri = getSingleUri(dobj, metaVocab.wasAcquiredBy)

		val acq = DataAcquisition(
			station = getStation(getSingleUri(acqUri, metaVocab.prov.wasAssociatedWith)),
			interval = for(
				start <- getOptionalInstant(acqUri, metaVocab.prov.startedAtTime);
				stop <- getOptionalInstant(acqUri, metaVocab.prov.endedAtTime)
			) yield TimeInterval(start, stop),
			instrument = getOptionalUri(acqUri, metaVocab.wasPerformedWith).map(_.toJava),
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
}
