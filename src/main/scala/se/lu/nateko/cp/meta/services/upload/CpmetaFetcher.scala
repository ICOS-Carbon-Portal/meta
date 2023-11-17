package se.lu.nateko.cp.meta.services.upload

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.metaflow.TcMetaSource
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.containsEither
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import scala.util.Try

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

	protected def getObjectFormat(format: IRI) = ObjectFormat(
		self = getLabeledResource(format),
		goodFlagValues = Some(server.getStringValues(format, metaVocab.hasGoodFlagValue)).filterNot(_.isEmpty)
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
			case Seq(single) => Some(Left(single.toJava))
			case many => Some(Right(many.map(_.toJava)))

	private def getNextVersions(item: IRI): Seq[IRI] = server
		.getStatements(None, Some(metaVocab.isNextVersionOf), Some(item))
		.flatMap{
			case Rdf4jStatement(next, _, _) =>
				if isPlainCollection(next)
				then server.getUriValues(next, metaVocab.dcterms.hasPart)
				else Seq(next)
			case _ => Nil //should not happen in practice, but just in case, for completeness
		}
		.filter(isComplete)
		.toIndexedSeq

	protected def isPlainCollection(item: IRI): Boolean =
		server.resourceHasType(item, metaVocab.plainCollectionClass)

	protected def isComplete(item: IRI): Boolean =
		import metaVocab.*
		val itemTypes = server.getUriValues(item, RDF.TYPE).toSet

		itemTypes.contains(collectionClass) || (
			itemTypes.contains(plainCollectionClass) &&
			server.getUriValues(item, dcterms.hasPart).exists(isComplete)
		) || (
			if itemTypes.containsEither(docObjectClass, dataObjectClass)
			then server.hasStatement(Some(item), Some(hasSizeInBytes), None)
			else true //we are probably using a wrong InstanceServer, so have to assume the item is complete
		)

	protected def getLatestVersion(item: IRI): OneOrSeq[URI] =

		def latest(item: IRI, seen: Set[IRI]): Seq[IRI] =
			val nextVersions = getNextVersions(item).flatMap{next =>
				if seen.contains(next)
				then Nil
				else latest(next, seen + next)
			}
			if nextVersions.isEmpty then Seq(item) else nextVersions

		latest(item, Set.empty).map(_.toJava) match
			case Seq(single) => Left(single)
			case many => Right(many)


	protected def getPreviousVersion(item: IRI): OptionalOneOrSeq[URI] =
		val allPrevVersions: Seq[IRI] =
			server.getUriValues(item, metaVocab.isNextVersionOf) ++
			server.getStatements(None, Some(metaVocab.dcterms.hasPart), Some(item)).flatMap{
				case Rdf4jStatement(coll, _, _) if isPlainCollection(coll) =>
					server.getUriValues(coll, metaVocab.isNextVersionOf)
				case _ => Nil
			}
		allPrevVersions.map(_.toJava) match
			case Nil => None
			case Seq(single) => Some(Left(single))
			case many => Some(Right(many))


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
			val flaggedCols = server.getUriValues(dv, metaVocab.isQualityFlagFor).map(_.toJava)
			DatasetVariable(
				self = getLabeledResource(dv),
				title = getSingleString(dv, titleProp),
				valueType = getValueType(getSingleUri(dv, metaVocab.hasValueType)),
				valueFormat = getOptionalUri(dv, metaVocab.hasValueFormat).map(_.toJava),
				isFlagFor = Some(flaggedCols).filterNot(_.isEmpty),
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

		val comments = server.getStringValues(instr, RDFS.COMMENT)

		UriResource(instr.toJava, Some(label), comments)
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











class CpmetaReader(val metaVocab: CpmetaVocab):
	import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*

	def getPlainStaticObject(dobj: IRI): TSC2V[PlainStaticObject] =
		for
			hashsum <- getHashsum(dobj, metaVocab.hasSha256sum)
			fileName <- getOptionalString(dobj, metaVocab.dcterms.title).flatMap:
				case None => getSingleString(dobj, metaVocab.hasName)
				case Some(title) => Validated.ok(title)
		yield
			PlainStaticObject(dobj.toJava, hashsum, fileName)


	def getOptionalSpecificationFormat(spec: IRI): TSC2V[Option[IRI]] =
		getOptionalUri(spec, metaVocab.hasFormat)

	def getPosition(iri: IRI): TSC2V[Position] =
		for
			latLon <- getLatLon(iri)
			altOpt <- getOptionalFloat(iri, metaVocab.hasElevation)
			lblOpt <- getOptionalString(iri, RDFS.LABEL)
		yield
			latLon.copy(alt = altOpt, label = lblOpt, uri = Some(iri.toJava))

	def getInstrumentPosition(deploymentIri: IRI): TSC2V[Position] =
		for
			latLon <- getLatLon(deploymentIri)
			altOpt <- getOptionalFloat(deploymentIri, metaVocab.hasSamplingHeight)
		yield
			latLon.copy(alt = altOpt)

	private def getLatLon(iri: IRI): TSC2V[Position] =
		for
			lat <- getSingleDouble(iri, metaVocab.hasLatitude)
			lon <- getSingleDouble(iri, metaVocab.hasLongitude)
		yield
			Position.ofLatLon(lat, lon)

	def getLatLonBox(cov: IRI): TSC2V[LatLonBox] =
		for
			minLat <- getSingleDouble(cov, metaVocab.hasSouthernBound)
			minLon <- getSingleDouble(cov, metaVocab.hasWesternBound)
			maxLat <- getSingleDouble(cov, metaVocab.hasNorthernBound)
			maxLon <- getSingleDouble(cov, metaVocab.hasEasternBound)
			lblOpt <- getOptionalString(cov, RDFS.LABEL)
		yield
			LatLonBox(
				min = Position.ofLatLon(minLat, minLon),
				max = Position.ofLatLon(maxLat, maxLon),
				label = lblOpt,
				uri = Some(cov.toJava)
			)

	def getSubmission(subm: IRI): TSC2V[DataSubmission] =
		for
			submitterUri <- getSingleUri(subm, metaVocab.prov.wasAssociatedWith)
			submitter <- getOrganization(submitterUri)
			start <- getSingleInstant(subm, metaVocab.prov.startedAtTime)
			stop <- getOptionalInstant(subm, metaVocab.prov.endedAtTime)
		yield
			DataSubmission(
				submitter = submitter,
				start = start,
				stop = stop
			)

	def getAgent(uri: IRI): TSC2V[Agent] =
		getOptionalString(uri, metaVocab.hasFirstName).flatMap: a =>
			if a.isDefined then
				getPerson(uri)
			else
				getOrganization(uri)

	def getOrganization(org: IRI): TSC2V[Organization] =
		for
			self <- getLabeledResource(org)
			name <- getSingleString(org, metaVocab.hasName)
			emailOpt <- getOptionalString(org, metaVocab.hasEmail)
			websiteOpt <- getOptionalUri(org, RDFS.SEEALSO)
			webpageUriOpt <- getOptionalUri(org, metaVocab.hasWebpageElements)
			webpageDetailsOpt <- webpageUriOpt.map(getWebpageElems).sinkOption
		yield
			Organization(
				self = self,
				name = name,
				email = emailOpt,
				website = websiteOpt.map(_.toJava),
				webpageDetails = webpageDetailsOpt
			)

	def getWebpageElems(elems: IRI): TSC2V[WebpageElements] =
		for
			self <- getLabeledResource(elems)
			coverImageOpt <- getOptionalUriLiteral(elems, metaVocab.hasCoverImage)
			linkBoxes <- Validated.sequence(getUriValues(elems, metaVocab.hasLinkbox).map(getLinkBox))
		yield
			WebpageElements(
				self = self,
				coverImage = coverImageOpt,
				linkBoxes = Option(linkBoxes.sortBy(_.orderWeight)).filterNot(_.isEmpty)
			)

	def getLinkBox(lbox: IRI): TSC2V[LinkBox] =
		for
			name <- getSingleString(lbox, metaVocab.hasName)
			coverImage <- getSingleUriLiteral(lbox, metaVocab.hasCoverImage)
			target <- getSingleUriLiteral(lbox, metaVocab.hasWebpageLink)
			orderWeightOpt <- getOptionalInt(lbox, metaVocab.hasOrderWeight)
		yield
			LinkBox(
				name = name,
				coverImage = coverImage,
				target = target,
				orderWeight = orderWeightOpt
			)

	def getPerson(pers: IRI): TSC2V[Person] =
		for
			self <- getLabeledResource(pers)
			firstName <- getSingleString(pers, metaVocab.hasFirstName)
			lastName <- getSingleString(pers, metaVocab.hasLastName)
			emailOpt <- getOptionalString(pers, metaVocab.hasEmail)
			orcidOpt <- getOptionalString(pers, metaVocab.hasOrcidId)
		yield
			Person(
				self = self,
				firstName = firstName,
				lastName = lastName,
				email = emailOpt,
				orcid = orcidOpt.flatMap(Orcid.unapply)
			)

	def getProject(project: IRI): TSC2V[Project] =
		for
			self <- getLabeledResource(project)
			keywordsOpt <- getOptionalString(project, metaVocab.hasKeywords)
		yield
			Project(
				self = self,
				keywords = keywordsOpt.map(s => parseCommaSepList(s).toIndexedSeq)
			)

	def getObjectFormat(format: IRI): TSC2V[ObjectFormat] =
		for
			self <- getLabeledResource(format)
		yield
			ObjectFormat(
				self = self,
				goodFlagValues = Some(getStringValues(format, metaVocab.hasGoodFlagValue)).filterNot(_.isEmpty)
			)

	def getDataTheme(theme: IRI): TSC2V[DataTheme] =
		for
			self <- getLabeledResource(theme)
			icon <- getSingleUriLiteral(theme, metaVocab.hasIcon)
			markerIconOpt <- getOptionalUriLiteral(theme, metaVocab.hasMarkerIcon)
		yield DataTheme(self = self, icon = icon, markerIcon = markerIconOpt)

	def getTemporalCoverage(dobj: IRI): TSC2V[TemporalCoverage] =
		for
			start <- getSingleInstant(dobj, metaVocab.hasStartTime)
			stop <- getSingleInstant(dobj, metaVocab.hasEndTime)
			resolutionOpt <- getOptionalString(dobj, metaVocab.hasTemporalResolution)
		yield
			TemporalCoverage(
				interval = TimeInterval(
					start = start,
					stop = stop
				),
				resolution = resolutionOpt
			)

	def getStationLocation(stat: IRI, labelOpt: Option[String]): TSC2V[Position] =
		for
			posLat <- getSingleDouble(stat, metaVocab.hasLatitude)
			posLon <- getSingleDouble(stat, metaVocab.hasLongitude)
			altOpt <- getOptionalFloat(stat, metaVocab.hasElevation)
			stLabelOpt <- getOptionalString(stat, RDFS.LABEL).orElse(labelOpt)
		yield
			Position(posLat, posLon, altOpt, stLabelOpt, None)

	def getSite(site: IRI): TSC2V[Site] =
		for
			self <- getLabeledResource(site)
			ecosystem <- getLabeledResource(site, metaVocab.hasEcosystemType)
			locationUriOpt <- getOptionalUri(site, metaVocab.hasSpatialCoverage)
			location <- locationUriOpt.map(getCoverage).sinkOption
		yield
			Site(
				self = self,
				ecosystem = ecosystem,
				location = location
			)

	def getCoverage(covUri: IRI): TSC2V[GeoFeature] =
		getSingleUri(covUri, RDF.TYPE).flatMap: covClass =>
			if covClass === metaVocab.latLonBoxClass then
				getLatLonBox(covUri)
			else if covClass === metaVocab.positionClass then
				getPosition(covUri).require(s"Could not read Position from URI $covUri")
			else
				for
					geoJson <- getSingleString(covUri, metaVocab.asGeoJSON)
					labelOpt <- getOptionalString(covUri, RDFS.LABEL)
					feature <- Validated.fromTry(GeoJson.toFeature(geoJson))
				yield
					feature.withOptLabel(labelOpt).withUri(covUri.toJava)

	def getNextVersionAsUri(item: IRI): TSC2[OptionalOneOrSeq[URI]] =
		getNextVersions(item) match
			case Nil => None
			case Seq(single) => Some(Left(single.toJava))
			case many => Some(Right(many.map(_.toJava)))

	private def getNextVersions(item: IRI): TSC2[Seq[IRI]] =
		getStatements(None, Some(metaVocab.isNextVersionOf), Some(item))
		.flatMap:
			case Rdf4jStatement(next, _, _) =>
				if isPlainCollection(next)
				then getUriValues(next, metaVocab.dcterms.hasPart)
				else Seq(next)
			case _ => Nil //should not happen in practice, but just in case, for completeness
		.filter(isComplete)
		.toIndexedSeq

	def isPlainCollection(item: IRI): TSC2[Boolean] =
		resourceHasType(item, metaVocab.plainCollectionClass)

	def isComplete(item: IRI): TSC2[Boolean] =
		import metaVocab.*
		val itemTypes = getUriValues(item, RDF.TYPE).toSet
		itemTypes.contains(collectionClass) || (
			itemTypes.contains(plainCollectionClass) &&
			getUriValues(item, dcterms.hasPart).exists(isComplete)
		) || (
			if itemTypes.containsEither(docObjectClass, dataObjectClass)
			then hasStatement(Some(item), Some(hasSizeInBytes), None)
			else true //we are probably using a wrong context, so have to assume the item is complete
		)

	def getLatestVersion(item: IRI): TSC2[OneOrSeq[URI]] =
		def latest(item: IRI, seen: Set[IRI]): TSC2[Seq[IRI]] =
			val nextVersions = getNextVersions(item).flatMap: next =>
				if seen.contains(next)
				then Nil
				else latest(next, seen + next)
			if nextVersions.isEmpty then Seq(item) else nextVersions
		latest(item, Set.empty).map(_.toJava) match
			case Seq(single) => Left(single)
			case many => Right(many)

	def getPreviousVersion(item: IRI): TSC2[OptionalOneOrSeq[URI]] =
		val allPrevVersions: TSC2[Seq[IRI]] =
			getUriValues(item, metaVocab.isNextVersionOf) ++
			getStatements(None, Some(metaVocab.dcterms.hasPart), Some(item)).flatMap:
				case Rdf4jStatement(coll, _, _) if isPlainCollection(coll) =>
					getUriValues(coll, metaVocab.isNextVersionOf)
				case _ => Nil
		allPrevVersions.map(_.toJava) match
			case Nil => None
			case Seq(single) => Some(Left(single))
			case many => Some(Right(many))

	def getPreviousVersions(item: IRI): TSC2[Seq[URI]] = getPreviousVersion(item).flattenToSeq

	def getValTypeLookup(datasetSpec: IRI): TSC2V[VarMetaLookup] =
		for
			datasetVars <- getDatasetVars(datasetSpec)
			datasetColumns <- getDatasetColumns(datasetSpec)
		yield
			VarMetaLookup(datasetVars ++ datasetColumns)

	def getL3VarInfo(vi: IRI, vtLookup: VarMetaLookup): TSC2V[Option[VarMeta]] =
		for
			minValue <- getOptionalDouble(vi, metaVocab.hasMinValue)
			maxValue <- getOptionalDouble(vi, metaVocab.hasMaxValue)
			varNameOpt <- getOptionalString(vi, RDFS.LABEL)
		yield
			for
				varName <- varNameOpt
				varMeta <- vtLookup.lookup(varName)
			yield
				varMeta.copy(
					minMax = minValue.flatMap(min => maxValue.map(min -> _))
				)

	def getValueType(vt: IRI): TSC2V[ValueType] =
		for
			labeledResource <- getLabeledResource(vt)
			quantityKindUri <- getOptionalUri(vt, metaVocab.hasQuantityKind)
			quantityKind <- quantityKindUri.map(getLabeledResource).sinkOption
			unit <- getOptionalString(vt, metaVocab.hasUnit)
		yield
			ValueType(labeledResource, quantityKind, unit)

	private def getDatasetVars(ds: IRI): TSC2V[Seq[DatasetVariable]] =
		import metaVocab.*
		getDatasetVarsOrCols(ds, hasVariable, hasVariableTitle, isRegexVariable, isOptionalVariable)

	private def getDatasetColumns(ds: IRI): TSC2V[Seq[DatasetVariable]] =
		import metaVocab.*
		getDatasetVarsOrCols(ds, hasColumn, hasColumnTitle, isRegexColumn, isOptionalColumn)

	private def getDatasetVarsOrCols(ds: IRI, varProp: IRI, titleProp: IRI, regexProp: IRI, optProp: IRI): TSC2V[Seq[DatasetVariable]] =
		Validated.sequence(getUriValues(ds, varProp).map: dv =>
			for
				self <- getLabeledResource(dv)
				title <- getSingleString(dv, titleProp)
				valueTypeUri <- getSingleUri(dv, metaVocab.hasValueType)
				valueType <- getValueType(valueTypeUri)
				valueFormat <- getOptionalUri(dv, metaVocab.hasValueFormat)
				isRegex <- getOptionalBool(dv, regexProp)
				isOptional <- getOptionalBool(dv, optProp)
			yield
				val flaggedCols = getUriValues(dv, metaVocab.isQualityFlagFor).map(_.toJava)
				DatasetVariable(
					self = self,
					title = title,
					valueType = valueType,
					valueFormat = valueFormat.map(_.toJava),
					isFlagFor = Some(flaggedCols).filterNot(_.isEmpty),
					isRegex = isRegex.getOrElse(false),
					isOptional = isOptional.getOrElse(false)
				)
		)

	def getInstrumentLite(instr: IRI): TSC2V[UriResource] =
		val modelValid = getOptionalString(instr, metaVocab.hasModel).map(model => model.filter(_ != TcMetaSource.defaultInstrModel))
		val serialNumberValid = getOptionalString(instr, metaVocab.hasSerialNumber).map(serialNumber => serialNumber.filter(_ != TcMetaSource.defaultSerialNum))

		for
			model <- modelValid
			serialNumber <- serialNumberValid
			name <- getOptionalString(instr, metaVocab.hasName)
		yield
			val label = name.orElse:
				(model, serialNumber) match
					case (None, None) => None
					case (None, nbr) => nbr
					case (m, None) => m
					case (Some(m), Some(nbr)) => Some(m + " (" + nbr + ")")
			.getOrElse:
				instr.getLocalName

			val comments = getStringValues(instr, RDFS.COMMENT)

			UriResource(instr.toJava, Some(label), comments)

	def getInstrumentDeployment(iri: IRI, instrument: IRI): TSC2V[InstrumentDeployment] =
		for
			stationIri <- getSingleUri(iri, metaVocab.atOrganization)
			instrument <- getInstrumentLite(instrument)
			station <- getOrganization(stationIri)
			pos <- getInstrumentPosition(iri).optional
			variableNameOpt <- getOptionalString(iri, metaVocab.hasVariableName)
			forPropertyOpt <- getOptionalUri(iri, metaVocab.ssn.forProperty)
			forProperty <- forPropertyOpt.map(getLabeledResource).sinkOption
			start <- getOptionalInstant(iri, metaVocab.hasStartTime)
			stop <- getOptionalInstant(iri, metaVocab.hasEndTime)
		yield
			InstrumentDeployment(
				instrument = instrument,
				station = station,
				pos = pos,
				variableName = variableNameOpt,
				forProperty = forProperty,
				start = start,
				stop = stop
			)

	def getInstrument(instr: IRI): TSC2V[Instrument] =
		if resourceHasType(instr, metaVocab.instrumentClass) then
			for
				self <- getInstrumentLite(instr)
				model <- getSingleString(instr, metaVocab.hasModel)
				serialNumber <- getSingleString(instr, metaVocab.hasSerialNumber)
				name <- getOptionalString(instr, metaVocab.hasName)
				vendor <- getOptionalUri(instr, metaVocab.hasVendor)
				vendorOrg <- vendor.map(getOrganization).sinkOption
				owner <- getOptionalUri(instr, metaVocab.hasInstrumentOwner)
				ownerOrg <- owner.map(getOrganization).sinkOption
				parts <- Validated.sequence(getUriValues(instr, metaVocab.hasInstrumentComponent).map(getInstrumentLite))
				partOf <- getStatements(None, Some(metaVocab.hasInstrumentComponent), Some(instr))
					.map(_.getSubject).collect{ case iri: IRI => getInstrumentLite(iri)}.toList.headOption.sinkOption
				deployments <- Validated.sequence(getUriValues(instr, metaVocab.ssn.hasDeployment).map(getInstrumentDeployment(_, instr)))
			yield
				Instrument(
					self = self,
					model = model,
					serialNumber = serialNumber,
					name = name,
					vendor = vendorOrg,
					owner = ownerOrg,
					parts = parts,
					partOf = partOf,
					deployments = deployments
				)
		else
			Validated.error(s"$instr is not an instrument")

end CpmetaReader
