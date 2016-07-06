package se.lu.nateko.cp.meta.services.upload

import java.net.{URI => JavaUri}

import org.openrdf.model.URI
import org.openrdf.model.vocabulary.RDF

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.core.data.DataTheme._
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.sesame._

class DataObjectFetcher(
	protected val server: InstanceServer,
	vocab: CpVocab,
	protected val metaVocab: CpmetaVocab,
	pidFactory: Sha256Sum => String
) extends CpmetaFetcher {

	private implicit val factory = metaVocab.factory

	def fetch(hash: Sha256Sum): Option[DataObject] = {
		val dataObjUri = vocab.getDataObject(hash)
		if(server.hasStatement(dataObjUri, RDF.TYPE, metaVocab.dataObjectClass))
			Some(getExistingDataObject(hash))
		else None
	}

	private def getExistingDataObject(hash: Sha256Sum): DataObject = {
		val dobj = vocab.getDataObject(hash)

		val production: Option[DataProduction] = getOptionalUri(dobj, metaVocab.wasProducedBy)
			.map(getDataProduction)

		val fileName = getOptionalString(dobj, metaVocab.hasName)
		val spec = getSpecification(getSingleUri(dobj, metaVocab.hasObjectSpec))
		val submission = getSubmission(getSingleUri(dobj, metaVocab.wasSubmittedBy))

		DataObject(
			hash = getHashsum(dobj, metaVocab.hasSha256sum),
			accessUrl = getAccessUrl(hash, fileName, spec.format.uri),
			fileName = fileName,
			pid = submission.stop.flatMap(_ => getPid(hash, spec.format.uri)),
			submission = submission,
			specification = spec,
			specificInfo = getL3Meta(dobj, production).map(Left(_)).getOrElse(Right(getL2Meta(dobj, production)))
		)
	}

	private def getPid(hash: Sha256Sum, format: URI): Option[String] = {
		if(format == metaVocab.wdcggFormat) None else Some(pidFactory(hash))
	}

	private def getAccessUrl(hash: Sha256Sum, fileName: Option[String], specFormat: URI): Option[JavaUri] = {
		if(specFormat == metaVocab.wdcggFormat) None
		else Some(vocab.getDataObjectAccessUrl(hash, fileName))
	}

	private def getTheme(subj: URI): DataTheme = {
		import metaVocab.{ atc, atmoStationClass, cal, cfClass, cp, ecoStationClass, etc, oceStationClass, orgClass, otc, tcClass }

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
			case `orgClass` => NonICOS
		}
		themes.headOption.getOrElse(NonICOS)
	}

	private def getTemporalCoverage(dobj: URI) = TemporalCoverage(
		interval = TimeInterval(
			start = getSingleInstant(dobj, metaVocab.hasStartTime),
			stop = getSingleInstant(dobj, metaVocab.hasEndTime)
		),
		resolution = getOptionalString(dobj, metaVocab.hasTemporalResolution)
	)

	private def getStation(stat: URI) = Station(
		uri = stat,
		id = getOptionalString(stat, metaVocab.hasStationId).getOrElse("Unknown"),
		name = getOptionalString(stat, metaVocab.hasName).getOrElse("Unknown"),
		theme = getTheme(stat),
		pos = for(
			posLat <- getOptionalDouble(stat, metaVocab.hasLatitude);
			posLon <- getOptionalDouble(stat, metaVocab.hasLongitude)
		) yield Position(posLat, posLon),
		//TODO: Add support for geoJson from station info (OTC)
		coverage = None
	)

	private def getL3Meta(dobj: URI, prodOpt: Option[DataProduction]): Option[L3SpecificMeta] =
		getOptionalUri(dobj, metaVocab.hasSpatialCoverage).map{ cov =>
			assert(prodOpt.isDefined, "Production info must be provided for a spatial data object")
			val prod = prodOpt.get
			L3SpecificMeta(
				title = getSingleString(dobj, metaVocab.dcterms.title),
				description = getOptionalString(dobj, metaVocab.dcterms.description),
				spatial = getSpatialCoverage(cov),
				temporal = getTemporalCoverage(dobj),
				productionInfo = prod,
				theme = getTheme(prod.hostOrganization.getOrElse(prod.creator).uri)
			)
		}

	private def getL2Meta(dobj: URI, prod: Option[DataProduction]): L2OrLessSpecificMeta = {
		val acqUri = getSingleUri(dobj, metaVocab.wasAcquiredBy)
		val acq = DataAcquisition(
			station = getStation(getSingleUri(acqUri, metaVocab.prov.wasAssociatedWith)),
			interval = for(
				start <- getOptionalInstant(acqUri, metaVocab.prov.startedAtTime);
				stop <- getOptionalInstant(acqUri, metaVocab.prov.endedAtTime)
			) yield TimeInterval(start, stop)
		)
		L2OrLessSpecificMeta(acq, prod)
	}

}
