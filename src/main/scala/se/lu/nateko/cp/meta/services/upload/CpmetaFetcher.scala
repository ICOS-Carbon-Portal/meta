package se.lu.nateko.cp.meta.services.upload

import org.openrdf.model.URI
import org.openrdf.model.vocabulary.RDF
import org.openrdf.model.vocabulary.RDFS

import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.sesame._

import DataTheme._
import OrganizationClass.OrganizationClass

trait CpmetaFetcher extends FetchingHelper{
	protected def metaVocab: CpmetaVocab

	protected def getSpecification(spec: URI) = DataObjectSpec(
		self = getLabeledResource(spec),
		format = getLabeledResource(spec, metaVocab.hasFormat),
		encoding = getLabeledResource(spec, metaVocab.hasEncoding),
		dataLevel = getSingleInt(spec, metaVocab.hasDataLevel),
		datasetSpec = None
	)

	protected def getSpatialCoverage(cov: URI) = SpatialCoverage(
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

	protected def getDataProduction(prod: URI) = DataProduction(
		creator = getAgent(getSingleUri(prod, metaVocab.wasPerformedBy)),
		contributors = server.getUriValues(prod, metaVocab.wasParticipatedInBy).map(getAgent),
		host = getOptionalUri(prod, metaVocab.wasHostedBy).map(getOrganization),
		comment = getOptionalString(prod, RDFS.COMMENT),
		dateTime = getSingleInstant(prod, metaVocab.hasEndTime)
	)

	protected def getSubmission(subm: URI): DataSubmission = {
		val submitter: URI = getSingleUri(subm, metaVocab.prov.wasAssociatedWith)
		DataSubmission(
			submitter = getOrganization(submitter),
			start = getSingleInstant(subm, metaVocab.prov.startedAtTime),
			stop = getOptionalInstant(subm, metaVocab.prov.endedAtTime)
		)
	}

	private def getAgent(uri: URI): Agent = {
		if(getOptionalString(uri, metaVocab.hasFirstName).isDefined)
			getPerson(uri)
		else getOrganization(uri)
	}

	private def getOrganization(org: URI) = Organization(
		self = getLabeledResource(org),
		name = getSingleString(org, metaVocab.hasName),
		orgClass = getOrgClass(org)
	)

	private def getPerson(pers: URI) = Person(
		self = getLabeledResource(pers),
		firstName = getSingleString(pers, metaVocab.hasFirstName),
		lastName = getSingleString(pers, metaVocab.hasLastName)
	)

	private def getOrgClass(subj: URI): OrganizationClass = {
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

	private def getDataTheme(subj: URI): DataTheme = {
		val vocab = metaVocab
		import vocab.{ atc, atmoStationClass, cal, cfClass, cp, ecoStationClass, etc, oceStationClass, orgClass, otc, tcClass }

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

	private def getTemporalCoverage(dobj: URI) = TemporalCoverage(
		interval = TimeInterval(
			start = getSingleInstant(dobj, metaVocab.hasStartTime),
			stop = getSingleInstant(dobj, metaVocab.hasEndTime)
		),
		resolution = getOptionalString(dobj, metaVocab.hasTemporalResolution)
	)

	private def getStation(stat: URI) = Station(
		org = getOrganization(stat),
		id = getOptionalString(stat, metaVocab.hasStationId).getOrElse("Unknown"),
		name = getOptionalString(stat, metaVocab.hasName).getOrElse("Unknown"),
		theme = getDataTheme(stat),
		pos = for(
			posLat <- getOptionalDouble(stat, metaVocab.hasLatitude);
			posLon <- getOptionalDouble(stat, metaVocab.hasLongitude)
		) yield Position(posLat, posLon),
		//TODO: Add support for geoJson from station info (OTC)
		coverage = None
	)

	protected def getL3Meta(dobj: URI, prodOpt: Option[DataProduction]): Option[L3SpecificMeta] = {
		implicit val factory = metaVocab.factory

		getOptionalUri(dobj, metaVocab.hasSpatialCoverage).map{ cov =>
			assert(prodOpt.isDefined, "Production info must be provided for a spatial data object")
			val prod = prodOpt.get

			L3SpecificMeta(
				title = getSingleString(dobj, metaVocab.dcterms.title),
				description = getOptionalString(dobj, metaVocab.dcterms.description),
				spatial = getSpatialCoverage(cov),
				temporal = getTemporalCoverage(dobj),
				productionInfo = prod,
				theme = getDataTheme(prod.host.map(_.self).getOrElse(prod.creator.self).uri)
			)
		}
	}

	protected def getL2Meta(dobj: URI, prod: Option[DataProduction]): L2OrLessSpecificMeta = {
		val acqUri = getSingleUri(dobj, metaVocab.wasAcquiredBy)
		val acq = DataAcquisition(
			station = getStation(getSingleUri(acqUri, metaVocab.prov.wasAssociatedWith)),
			interval = for(
				start <- getOptionalInstant(acqUri, metaVocab.prov.startedAtTime);
				stop <- getOptionalInstant(acqUri, metaVocab.prov.endedAtTime)
			) yield TimeInterval(start, stop)
		)
		val nRows = getOptionalInt(dobj, metaVocab.hasNumberOfRows)
		L2OrLessSpecificMeta(acq, prod, nRows)
	}

}
