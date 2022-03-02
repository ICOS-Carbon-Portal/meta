package se.lu.nateko.cp.meta.services.upload

import java.net.URI

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.icos.TcMetaSource
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.utils.parseCommaSepList

import scala.util.Try

trait CpmetaFetcher extends FetchingHelper{
	protected final lazy val metaVocab = new CpmetaVocab(server.factory)

	def getOptionalSpecificationFormat(spec: IRI): Option[IRI] = getOptionalUri(spec, metaVocab.hasFormat)

	protected def getPosition(point: IRI) = Position(
		lat = getSingleDouble(point, metaVocab.hasLatitude),
		lon = getSingleDouble(point, metaVocab.hasLongitude),
		Option.empty,
		label = getOptionalString(point, RDFS.LABEL)
	)

	protected def getLatLonBox(cov: IRI) = LatLonBox(
		min = Position(
			lat = getSingleDouble(cov, metaVocab.hasSouthernBound),
			lon = getSingleDouble(cov, metaVocab.hasWesternBound),
			Option.empty,
			None
		),
		max = Position(
			lat = getSingleDouble(cov, metaVocab.hasNorthernBound),
			lon = getSingleDouble(cov, metaVocab.hasEasternBound),
			Option.empty,
			None
		),
		label = getOptionalString(cov, RDFS.LABEL),
		uri = Some(cov.toJava)
	)

	protected def getSubmission(subm: IRI): DataSubmission = {
		val submitter: IRI = getSingleUri(subm, metaVocab.prov.wasAssociatedWith)
		DataSubmission(
			submitter = getOrganization(submitter),
			start = getSingleInstant(subm, metaVocab.prov.startedAtTime),
			stop = getOptionalInstant(subm, metaVocab.prov.endedAtTime)
		)
	}

	protected def getAgent(uri: IRI): Agent = {
		if(getOptionalString(uri, metaVocab.hasFirstName).isDefined)
			getPerson(uri)
		else getOrganization(uri)
	}

	protected def getOrganization(org: IRI) = Organization(
		self = getLabeledResource(org),
		name = getSingleString(org, metaVocab.hasName),
		email = getOptionalString(org, metaVocab.hasEmail),
		website = getOptionalUri(org, RDFS.SEEALSO).map(_.toJava)
	)

	protected def getPerson(pers: IRI) = Person(
		self = getLabeledResource(pers),
		firstName = getSingleString(pers, metaVocab.hasFirstName),
		lastName = getSingleString(pers, metaVocab.hasLastName),
		orcid = getOptionalString(pers, metaVocab.hasOrcidId).flatMap(Orcid.unapply)
	)

	protected def getProject(project: IRI) = Project(
		self = getLabeledResource(project),
		keywords = getOptionalString(project, metaVocab.hasKeywords).map(s => parseCommaSepList(s).toIndexedSeq)
	)

	protected def getDataTheme(theme: IRI) = DataTheme(
		self = getLabeledResource(theme),
		icon = getSingleUriLiteral(theme, metaVocab.hasIcon),
		markerIcon = getOptionalUriLiteral(theme, metaVocab.hasMarkerIcon)
	)

	protected def getTemporalCoverage(dobj: IRI) = TemporalCoverage(
		interval = TimeInterval(
			start = getSingleInstant(dobj, metaVocab.hasStartTime),
			stop = getSingleInstant(dobj, metaVocab.hasEndTime)
		),
		resolution = getOptionalString(dobj, metaVocab.hasTemporalResolution)
	)

	protected def getStationLocation(stat: IRI, labelOpt: Option[String]): Option[Position] = for(
		posLat <- getOptionalDouble(stat, metaVocab.hasLatitude);
		posLon <- getOptionalDouble(stat, metaVocab.hasLongitude);
		altOpt = getOptionalFloat(stat, metaVocab.hasElevation);
		stLabel = getOptionalString(stat, RDFS.LABEL).orElse(labelOpt)
	) yield Position(posLat, posLon, altOpt, stLabel)

	protected def getSite(site: IRI) = Site(
		self = getLabeledResource(site),
		ecosystem = getLabeledResource(site, metaVocab.hasEcosystemType),
		location = getOptionalUri(site, metaVocab.hasSpatialCoverage).map(getCoverage)
	)

	protected def getCoverage(covUri: IRI): GeoFeature = {
		val covClass = getSingleUri(covUri, RDF.TYPE)

		if(covClass === metaVocab.latLonBoxClass)
			getLatLonBox(covUri)
		else
			GeoJson.toFeature(
				getSingleString(covUri, metaVocab.asGeoJSON)
			).get.withOptLabel(getOptionalString(covUri, RDFS.LABEL))
	}

	protected def getNextVersion(item: IRI): Option[URI] = {
		server.getStatements(None, Some(metaVocab.isNextVersionOf), Some(item))
			.toIndexedSeq.headOption.collect{
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

	def getValTypeLookup(datasetSpec: IRI): ValueTypeLookup[IRI] =
		new ValueTypeLookup(getDatasetVars(datasetSpec) ++ getDatasetColumns(datasetSpec))

	protected def getL3VarInfo(vi: IRI, vtLookup: ValueTypeLookup[IRI]): Option[VarMeta] = for(
		varName <- getOptionalString(vi, RDFS.LABEL);
		valTypeUri <- vtLookup.lookup(varName)
	) yield
		VarMeta(
			label = varName,
			valueType = getValueType(valTypeUri),
			minMax = getOptionalDouble(vi, metaVocab.hasMinValue).flatMap{min =>
				getOptionalDouble(vi, metaVocab.hasMaxValue).map(min -> _)
			}
		)


	protected def getValueType(vt: IRI) = ValueType(
		getLabeledResource(vt),
		getOptionalUri(vt, metaVocab.hasQuantityKind).map(getLabeledResource),
		getOptionalString(vt, metaVocab.hasUnit)
	)

	protected def getDatasetVars(ds: IRI): Seq[DatasetVariable[IRI]] = server.getUriValues(ds, metaVocab.hasVariable).map{dv =>
		new DatasetVariable[IRI](
			title = getSingleString(dv, metaVocab.hasVariableTitle),
			valueType = getSingleUri(dv, metaVocab.hasValueType),
			isRegex = getOptionalBool(dv, metaVocab.isRegexVariable).getOrElse(false),
			isOptional = getOptionalBool(dv, metaVocab.isOptionalVariable).getOrElse(false)
		)
	}

	protected def getDatasetColumns(ds: IRI): Seq[DatasetVariable[IRI]] = server.getUriValues(ds, metaVocab.hasColumn).map{dv =>
		new DatasetVariable[IRI](
			title = getSingleString(dv, metaVocab.hasColumnTitle),
			valueType = getSingleUri(dv, metaVocab.hasValueType),
			isRegex = getOptionalBool(dv, metaVocab.isRegexColumn).getOrElse(false),
			isOptional = getOptionalBool(dv, metaVocab.isOptionalColumn).getOrElse(false)
		)
	}

	protected def getInstrumentLite(instr: IRI): UriResource = {
		val label = getOptionalString(instr, metaVocab.hasName).orElse{
			getOptionalString(instr, metaVocab.hasModel).filter(_ != TcMetaSource.defaultInstrModel)
		}.orElse{
			getOptionalString(instr, metaVocab.hasSerialNumber).filter(_ != TcMetaSource.defaultSerialNum)
		}.getOrElse{
			instr.getLocalName
		}
		UriResource(instr.toJava, Some(label), Nil)
	}

	def getInstrument(instr: IRI): Option[Instrument] = {
		if(server.resourceHasType(instr, metaVocab.instrumentClass)) Some(
			Instrument(
				self = getInstrumentLite(instr),
				model = getSingleString(instr, metaVocab.hasModel),
				serialNumber = getSingleString(instr, metaVocab.hasSerialNumber),
				name = getOptionalString(instr, metaVocab.hasName),
				vendor = getOptionalUri(instr, metaVocab.hasVendor).map(getOrganization),
				owner = getOptionalUri(instr, metaVocab.hasInstrumentOwner).map(getOrganization),
				parts = server.getUriValues(instr, metaVocab.hasInstrumentComponent).map(getInstrumentLite),
				partOf = server.getStatements(None, Some(metaVocab.hasInstrumentComponent), Some(instr)).map(_.getSubject).collect{
					case iri: IRI => getInstrumentLite(iri)
				}.toList.headOption
			)
		) else None
	}

}
