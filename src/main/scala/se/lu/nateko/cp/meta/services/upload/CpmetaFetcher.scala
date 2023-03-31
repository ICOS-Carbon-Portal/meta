package se.lu.nateko.cp.meta.services.upload

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.icos.TcMetaSource
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.utils.flattenToSeq
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import scala.util.Try
import scala.annotation.tailrec


trait CpmetaFetcher extends FetchingHelper{
	protected final lazy val metaVocab = new CpmetaVocab(server.factory)

	def getOptionalSpecificationFormat(spec: IRI): Option[IRI] = getOptionalUri(spec, metaVocab.hasFormat)

	protected def getPosition(iri: IRI): Option[Position] =
		getLatLon(iri).map{_.copy(
			alt = getOptionalFloat(iri, metaVocab.hasElevation),
			label = getOptionalString(iri, RDFS.LABEL),
			uri = Some(iri.toJava)
		)}
	
	protected def getInstrumentPosition(deploymentIri: IRI): Option[Position] = 
		getLatLon(deploymentIri).map{_.copy(
			alt = getOptionalFloat(deploymentIri, metaVocab.hasSamplingHeight)
		)}

	private def getLatLon(iri: IRI): Option[Position] =
		for
			lat <- getOptionalDouble(iri, metaVocab.hasLatitude)
			lon <- getOptionalDouble(iri, metaVocab.hasLongitude)
		yield Position.ofLatLon(lat, lon)

	protected def getLatLonBox(cov: IRI) = LatLonBox(
		min = Position.ofLatLon(
			lat = getSingleDouble(cov, metaVocab.hasSouthernBound),
			lon = getSingleDouble(cov, metaVocab.hasWesternBound)
		),
		max = Position.ofLatLon(
			lat = getSingleDouble(cov, metaVocab.hasNorthernBound),
			lon = getSingleDouble(cov, metaVocab.hasEasternBound)
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

	def getOrganization(org: IRI) = Organization(
		self = getLabeledResource(org),
		name = getSingleString(org, metaVocab.hasName),
		email = getOptionalString(org, metaVocab.hasEmail),
		website = getOptionalUri(org, RDFS.SEEALSO).map(_.toJava),
		webpageDetails = getOptionalUri(org, metaVocab.hasWebpageElements).map(getWebpageElems)
	)

	def getWebpageElems(elems: IRI) = WebpageElements(
		self = getLabeledResource(elems),
		coverImage = getOptionalUriLiteral(elems, metaVocab.hasCoverImage),
		linkBoxes = Option(
			server.getUriValues(elems, metaVocab.hasLinkbox).map(getLinkBox).sortBy(_.orderWeight)
		).filterNot(_.isEmpty)
	)

	def getLinkBox(lbox: IRI) = LinkBox(
		name = getSingleString(lbox, metaVocab.hasName),
		coverImage = getSingleUriLiteral(lbox, metaVocab.hasCoverImage),
		target = getSingleUriLiteral(lbox, metaVocab.hasWebpageLink),
		orderWeight = getOptionalInt(lbox, metaVocab.hasOrderWeight)
	)

	def getPerson(pers: IRI) = Person(
		self = getLabeledResource(pers),
		firstName = getSingleString(pers, metaVocab.hasFirstName),
		lastName = getSingleString(pers, metaVocab.hasLastName),
		email = getOptionalString(pers, metaVocab.hasEmail),
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
	) yield Position(posLat, posLon, altOpt, stLabel, None)

	protected def getSite(site: IRI) = Site(
		self = getLabeledResource(site),
		ecosystem = getLabeledResource(site, metaVocab.hasEcosystemType),
		location = getOptionalUri(site, metaVocab.hasSpatialCoverage).map(getCoverage)
	)

	protected def getCoverage(covUri: IRI): GeoFeature =
		val covClass = getSingleUri(covUri, RDF.TYPE)

		if covClass === metaVocab.latLonBoxClass then
			getLatLonBox(covUri)
		else if covClass === metaVocab.positionClass then
			getPosition(covUri).getOrElse(throw MetadataException(s"Could not read Position from URI $covUri"))
		else GeoJson
			.toFeature(getSingleString(covUri, metaVocab.asGeoJSON))
			.get
			.withOptLabel(getOptionalString(covUri, RDFS.LABEL))
			.withUri(covUri.toJava)

	protected def getNextVersionAsUri(item: IRI): OptionalOneOrSeq[URI] = 
		getNextVersions(item) match
			case Nil => None
			case single :: Nil => Some(Left(single.toJava))
			case many => Some(Right(many.map(_.toJava)))

	protected def getNextVersions(item: IRI): Seq[IRI] = server
		.getStatements(None, Some(metaVocab.isNextVersionOf), Some(item))
		.toIndexedSeq
		.collect{
			case Rdf4jStatement(next, _, _) if isPlainCollection(next) => server.getUriValues(item, metaVocab.dcterms.hasPart)
			case Rdf4jStatement(next, _, _) if isComplete(next) => Seq(next)
		}.flatten
	
	protected def isPlainCollection(item: IRI): Boolean =
		val itemTypes = server.getUriValues(item, RDF.TYPE).toSet
		itemTypes.contains(metaVocab.plainCollectionClass)

	protected def isComplete(item: IRI): Boolean =
		val itemTypes = server.getUriValues(item, RDF.TYPE).toSet
		if itemTypes.contains(metaVocab.collectionClass) then true
		else if Seq(metaVocab.docObjectClass, metaVocab.dataObjectClass).exists(itemTypes.contains)
		then server.hasStatement(Some(item), Some(metaVocab.hasSizeInBytes), None)
		else true //we are probably using a wrong InstanceServer, so have to assume the item is complete

	protected def getLatestVersions(item: IRI): Seq[URI] =
		var seen: Set[IRI] = Set()
		// @tailrec // rewrite to make tail recursive? 
		def latest(item: IRI): Seq[IRI] = 
			val nextVersions = getNextVersions(item).flatMap{next =>
				println(s"new version of item: $next and seen: $seen")
				if seen.contains(next) then Seq(item)
				else 
					seen = seen + next
					latest(next)
			}

			if nextVersions.isEmpty then Seq(item) else nextVersions

		latest(item).map(_.toJava)

	protected def getLatestVersion(item: IRI): Either[URI, Seq[URI]] =
		getLatestVersions(item) match
			case Nil => Right(Seq.empty) // this case should not happen?
			case single :: Nil => Left(single)
			case many => Right(many)

	protected def getPreviousVersion(item: IRI): OptionalOneOrSeq[URI] =
		server.getUriValues(item, metaVocab.isNextVersionOf).map(_.toJava).toList match {
			case Nil => None
			case single :: Nil => Some(Left(single))
			case many => Some(Right(many))
		}

	protected def getPreviousVersions(item: IRI): Seq[URI] = getPreviousVersion(item).flattenToSeq

	def getValTypeLookup(datasetSpec: IRI) = VarMetaLookup(
		getDatasetVars(datasetSpec) ++ getDatasetColumns(datasetSpec)
	)

	protected def getL3VarInfo(vi: IRI, vtLookup: VarMetaLookup): Option[VarMeta] = for(
		varName <- getOptionalString(vi, RDFS.LABEL);
		varMeta <- vtLookup.lookup(varName)
	) yield
		varMeta.copy(
			minMax = getOptionalDouble(vi, metaVocab.hasMinValue).flatMap{min =>
				getOptionalDouble(vi, metaVocab.hasMaxValue).map(min -> _)
			}
		)


	protected def getValueType(vt: IRI) = ValueType(
		getLabeledResource(vt),
		getOptionalUri(vt, metaVocab.hasQuantityKind).map(getLabeledResource),
		getOptionalString(vt, metaVocab.hasUnit)
	)

	private def getDatasetVars(ds: IRI) =
		import metaVocab.*
		getDatasetVarsOrCols(ds, hasVariable, hasVariableTitle, isRegexVariable, isOptionalVariable)

	private def getDatasetColumns(ds: IRI) =
		import metaVocab.*
		getDatasetVarsOrCols(ds, hasColumn, hasColumnTitle, isRegexColumn, isOptionalColumn)

	private def getDatasetVarsOrCols(ds: IRI, varProp: IRI, titleProp: IRI, regexProp: IRI, optProp: IRI): Seq[DatasetVariable] =
		server.getUriValues(ds, varProp).map{dv =>
			DatasetVariable(
				self = getLabeledResource(dv),
				title = getSingleString(dv, titleProp),
				valueType = getValueType(getSingleUri(dv, metaVocab.hasValueType)),
				valueFormat = getOptionalUri(dv, metaVocab.hasValueFormat).map(_.toJava),
				isRegex = getOptionalBool(dv, regexProp).getOrElse(false),
				isOptional = getOptionalBool(dv, optProp).getOrElse(false)
			)
		}

	protected def getInstrumentLite(instr: IRI): UriResource = {
		inline def model = getOptionalString(instr, metaVocab.hasModel).filter(_ != TcMetaSource.defaultInstrModel)
		inline def serialNumber = getOptionalString(instr, metaVocab.hasSerialNumber).filter(_ != TcMetaSource.defaultSerialNum)

		val label = getOptionalString(instr, metaVocab.hasName).orElse{
			(model, serialNumber) match
				case (None, None) => None
				case (None, nbr) => nbr
				case (m, None) => m
				case (Some(m), Some(nbr)) => Some(m + " (" + nbr + ")")
		}.getOrElse{
			instr.getLocalName
		}

		UriResource(instr.toJava, Some(label), Nil)
	}

	def getInstrumentDeployment(iri: IRI, instrument: IRI): InstrumentDeployment =
		val stationIri = getSingleUri(iri, metaVocab.atOrganization)

		InstrumentDeployment(
			instrument = getInstrumentLite(instrument),
			station = getOrganization(stationIri),
			pos = getInstrumentPosition(iri),
			variableName = getOptionalString(iri, metaVocab.hasVariableName),
			forProperty = getOptionalUri(iri, metaVocab.ssn.forProperty).map(getLabeledResource),
			start = getOptionalInstant(iri, metaVocab.hasStartTime),
			stop = getOptionalInstant(iri, metaVocab.hasEndTime)
		)


	def getInstrument(instr: IRI): Option[Instrument] =
		if server.resourceHasType(instr, metaVocab.instrumentClass) then Some(
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
				}.toList.headOption,
				deployments = server.getUriValues(instr, metaVocab.ssn.hasDeployment).map(getInstrumentDeployment(_, instr))
			)
		) else None

}
