package se.lu.nateko.cp.meta.services.upload

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.utils.{Validated, containsEither, parseCommaSepList}

import java.net.URI
import java.time.Instant

trait CpmetaReader:
	import StatementSource.*
	import RdfLens.{MetaConn, DobjConn, DocConn, ItemConn}

	val metaVocab: CpmetaVocab

	def getPlainDataObject(dobj: IRI)(using DobjConn): Validated[PlainStaticObject] =
		getPlainStaticObject(dobj)

	def getPlainDocObject(dobj: IRI)(using DocConn): Validated[PlainStaticObject] =
		getPlainStaticObject(dobj)

	private def getPlainStaticObject(dobj: IRI)(using StatementSource): Validated[PlainStaticObject] =
		for
			hashsum <- getHashsum(dobj, metaVocab.hasSha256sum)
			fileName <- getOptionalString(dobj, metaVocab.dcterms.title).flatMap:
				case None => getSingleString(dobj, metaVocab.hasName)
				case Some(title) => Validated.ok(title)
		yield
			PlainStaticObject(dobj.toJava, hashsum, fileName)


	def getPosition[C >: DobjConn <: MetaConn](iri: IRI): C ?=> Validated[Position] =
		for
			latLon <- getLatLon(iri)
			altOpt <- getOptionalFloat(iri, metaVocab.hasElevation)
			lblOpt <- getOptionalString(iri, RDFS.LABEL)
		yield
			latLon.copy(alt = altOpt, label = lblOpt, uri = Some(iri.toJava))

	def getInstrumentPosition(deploymentIri: IRI): MetaConn ?=> Validated[Position] =
		for
			latLon <- getLatLon[MetaConn](deploymentIri)
			altOpt <- getOptionalFloat(deploymentIri, metaVocab.hasSamplingHeight)
		yield
			latLon.copy(alt = altOpt)

	def getLatLon[C >: DobjConn <: MetaConn](iri: IRI)(using C): Validated[Position] =
		for
			lat <- getSingleDouble(iri, metaVocab.hasLatitude)
			lon <- getSingleDouble(iri, metaVocab.hasLongitude)
		yield
			Position.ofLatLon(lat, lon)

	def getLatLonBox[C >: DobjConn <: MetaConn](cov: IRI)(using C): Validated[LatLonBox] =
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

	def getSubmission(subm: IRI): MetaConn ?=> Validated[DataSubmission] =
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

	def getAgent(uri: IRI): MetaConn ?=> Validated[Agent] =
		getOptionalString(uri, metaVocab.hasFirstName).flatMap:
			case Some(firstName) => getPerson(uri, firstName)
			case None => getOrganization(uri)

	def getOrganization(org: IRI): MetaConn ?=> Validated[Organization] =
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

	def getWebpageElems(elems: IRI): MetaConn ?=> Validated[WebpageElements] =
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

	def getLinkBox(lbox: IRI): MetaConn ?=> Validated[LinkBox] =
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

	def getPerson(pers: IRI): MetaConn ?=> Validated[Person] =
		getSingleString(pers, metaVocab.hasFirstName).flatMap(getPerson(pers, _))

	private def getPerson(pers: IRI, firstName: String): MetaConn ?=> Validated[Person] =
		for
			self <- getLabeledResource(pers)
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

	def getProject(project: IRI): MetaConn ?=> Validated[Project] =
		for
			self <- getLabeledResource(project)
			keywordsOpt <- getOptionalString(project, metaVocab.hasKeywords)
		yield
			Project(
				self = self,
				keywords = keywordsOpt.map(s => parseCommaSepList(s).toIndexedSeq)
			)

	def getObjectFormat(format: IRI): MetaConn ?=> Validated[ObjectFormat] =
		for
			self <- getLabeledResource(format)
		yield
			ObjectFormat(
				self = self,
				goodFlagValues = Some(getStringValues(format, metaVocab.hasGoodFlagValue)).filterNot(_.isEmpty)
			)

	def getDataTheme(theme: IRI): MetaConn ?=> Validated[DataTheme] =
		for
			self <- getLabeledResource(theme)
			icon <- getSingleUriLiteral(theme, metaVocab.hasIcon)
			markerIconOpt <- getOptionalUriLiteral(theme, metaVocab.hasMarkerIcon)
		yield DataTheme(self = self, icon = icon, markerIcon = markerIconOpt)

	def getTemporalCoverage[C <: DobjConn](dobj: IRI): C ?=> Validated[TemporalCoverage] =
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

	def getStationLocation(stat: IRI, labelOpt: Option[String]): MetaConn ?=> Validated[Position] =
		for
			posLat <- getSingleDouble(stat, metaVocab.hasLatitude)
			posLon <- getSingleDouble(stat, metaVocab.hasLongitude)
			altOpt <- getOptionalFloat(stat, metaVocab.hasElevation)
			stLabelOpt <- getOptionalString(stat, RDFS.LABEL)
		yield
			Position(posLat, posLon, altOpt, stLabelOpt.orElse(labelOpt), None)

	def getSite(site: IRI): MetaConn ?=> Validated[Site] =
		for
			self <- getLabeledResource(site)
			ecosystem <- getLabeledResource(site, metaVocab.hasEcosystemType)
			locationUriOpt <- getOptionalUri(site, metaVocab.hasSpatialCoverage)
			location <- locationUriOpt.map(getCoverage[MetaConn]).sinkOption
		yield
			Site(
				self = self,
				ecosystem = ecosystem,
				location = location
			)

	def getCoverage[C <: MetaConn](covUri: IRI): C ?=> Validated[GeoFeature] =
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

	def getNextVersionAsUri(item: IRI)(using ItemConn): OptionalOneOrSeq[URI] =
		OptionalOneOrSeq.fromSeq(getNextVersions(item).map(_.toJava))

	private def getNextVersions(item: IRI)(using ItemConn): IndexedSeq[IRI] =
		getPropValueHolders(metaVocab.isNextVersionOf, item)
			.flatMap: next =>
				if isPlainCollection(next)
				then getUriValues(next, metaVocab.dcterms.hasPart)
				else Seq(next)
			.filter(isComplete)
			.filterNot(isUnderMoratorium)
			.toIndexedSeq

	// only the end of the submission matters here, not the submission (and its submitter) in full
	private def isUnderMoratorium(item: IRI)(using ItemConn): Boolean =
		getSingleUri(item, metaVocab.wasSubmittedBy)
			.flatMap(getOptionalInstant(_, metaVocab.prov.endedAtTime))
			.result
			.flatten
			.exists(_.isAfter(Instant.now))

	def isPlainCollection[C <: ItemConn](item: IRI): C ?=> Boolean =
		resourceHasType(item, metaVocab.plainCollectionClass)

	def isComplete(item: IRI)(using ItemConn): Boolean =
		import metaVocab.*
		val itemTypes = getUriValues(item, RDF.TYPE).toSet
		itemTypes.contains(collectionClass) || (
			itemTypes.contains(plainCollectionClass) &&
			getUriValues(item, dcterms.hasPart).exists(isComplete)
		) || (
			if itemTypes.containsEither(docObjectClass, dataObjectClass)
			then hasStatement(item, hasSizeInBytes, null)
			else true //we are probably using a wrong context, so have to assume the item is complete
		)

	def getLatestVersion(item: IRI)(using ItemConn): OneOrSeq[URI] =
		getLatestVersion(item, getNextVersions(item))

	/** The next and the latest versions of the item, with the next versions looked up only once */
	def getNextAndLatestVersions(item: IRI)(using ItemConn): (OptionalOneOrSeq[URI], OneOrSeq[URI]) =
		val nextVersions = getNextVersions(item)
		OptionalOneOrSeq.fromSeq(nextVersions.map(_.toJava)) -> getLatestVersion(item, nextVersions)

	private def getLatestVersion(item: IRI, nextVersions: IndexedSeq[IRI])(using ItemConn): OneOrSeq[URI] =
		def latest(item: IRI, nextVersions: IndexedSeq[IRI], seen: Set[IRI]): Seq[IRI] =
			val latestVersions = nextVersions.flatMap: next =>
				if seen.contains(next)
				then Nil
				else latest(next, getNextVersions(next), seen + next)
			if latestVersions.isEmpty then Seq(item) else latestVersions
		latest(item, nextVersions, Set.empty).map(_.toJava) match
			case Seq(single) => Left(single)
			case many => Right(many)

	def getPreviousVersion[C <: ItemConn](item: IRI): C ?=> OptionalOneOrSeq[IRI] =
		OptionalOneOrSeq.fromSeq(getPreviousVersions(item))

	def getPreviousVersions(item: IRI)(using ItemConn): IndexedSeq[IRI] =
		getUriValues(item, metaVocab.isNextVersionOf) ++
		getPropValueHolders(metaVocab.dcterms.hasPart, item).flatMap: coll =>
			if isPlainCollection(coll) then
				getUriValues(coll, metaVocab.isNextVersionOf)
			else Nil

	def getValTypeLookup(datasetSpec: IRI): MetaConn ?=> Validated[VarMetaLookup] =
		for
			datasetVars <- getDatasetVars(datasetSpec)
			datasetColumns <- getDatasetColumns(datasetSpec)
		yield
			VarMetaLookup(datasetVars ++ datasetColumns)

	def getL3VarInfo(vi: IRI, vtLookup: VarMetaLookup): DobjConn ?=> Validated[Option[VarMeta]] =
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

	def getValueType(vt: IRI): MetaConn ?=> Validated[ValueType] =
		for
			labeledResource <- getLabeledResource(vt)
			quantityKindUri <- getOptionalUri(vt, metaVocab.hasQuantityKind)
			quantityKind <- quantityKindUri.map(getLabeledResource).sinkOption
			unit <- getOptionalString(vt, metaVocab.hasUnit)
		yield
			ValueType(labeledResource, quantityKind, unit)

	private def getDatasetVars(ds: IRI): MetaConn ?=> Validated[Seq[DatasetVariable]] =
		import metaVocab.*
		getDatasetVarsOrCols(ds, hasVariable, hasVariableTitle, isRegexVariable, isOptionalVariable)

	private def getDatasetColumns(ds: IRI): MetaConn ?=> Validated[Seq[DatasetVariable]] =
		import metaVocab.*
		getDatasetVarsOrCols(ds, hasColumn, hasColumnTitle, isRegexColumn, isOptionalColumn)

	private def getDatasetVarsOrCols(
		ds: IRI, varProp: IRI, titleProp: IRI, regexProp: IRI, optProp: IRI
	): MetaConn ?=> Validated[Seq[DatasetVariable]] =
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

	def getInstrumentLite(instr: IRI): MetaConn ?=> Validated[UriResource] =
		for
			model <- getOptionalString(instr, metaVocab.hasModel)
			serialNumber <- getOptionalString(instr, metaVocab.hasSerialNumber)
			name <- getOptionalString(instr, metaVocab.hasName)
		yield
			getInstrumentLite(instr, model, serialNumber, name)

	private def getInstrumentLite(
		instr: IRI, modelOpt: Option[String], serialNumberOpt: Option[String], name: Option[String]
	): MetaConn ?=> UriResource =
		val model = modelOpt.filter(_ != CpmetaVocab.defaultInstrModel)
		val serialNumber = serialNumberOpt.filter(_ != CpmetaVocab.defaultSerialNum)

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

	/** A deployment at a station that has been read already */
	def getInstrumentDeployment(iri: IRI, instrument: IRI, station: Organization): MetaConn ?=> Validated[InstrumentDeployment] =
		getInstrumentLite(instrument).flatMap: instr =>
			readInstrumentDeployment(iri, instr): stationIri =>
				if station.self.uri === stationIri then Validated.ok(station)
				else Validated.error(s"Deployment $iri is at station $stationIri, not at ${station.self.uri}")

	private def getInstrumentDeployment(iri: IRI, instrument: UriResource): MetaConn ?=> Validated[InstrumentDeployment] =
		readInstrumentDeployment(iri, instrument)(getOrganization(_))

	private def readInstrumentDeployment(iri: IRI, instrument: UriResource)(
		getStation: IRI => Validated[Organization]
	): MetaConn ?=> Validated[InstrumentDeployment] =
		for
			stationIri <- getSingleUri(iri, metaVocab.atOrganization)
			station <- getStation(stationIri)
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

	def getInstrument(instr: IRI): MetaConn ?=> Validated[Instrument] =
		if resourceHasType(instr, metaVocab.instrumentClass) then
			for
				model <- getSingleString(instr, metaVocab.hasModel)
				serialNumber <- getSingleString(instr, metaVocab.hasSerialNumber)
				name <- getOptionalString(instr, metaVocab.hasName)
				self = getInstrumentLite(instr, Some(model), Some(serialNumber), name)
				vendor <- getOptionalUri(instr, metaVocab.hasVendor)
				vendorOrg <- vendor.map(getOrganization).sinkOption
				owner <- getOptionalUri(instr, metaVocab.hasInstrumentOwner)
				ownerOrg <- owner.map(getOrganization).sinkOption
				parts <- Validated.sequence(getUriValues(instr, metaVocab.hasInstrumentComponent).map(getInstrumentLite))
				partOf <- getPropValueHolders(metaVocab.hasInstrumentComponent, instr)
					.map(getInstrumentLite).headOption.sinkOption
				deployments <- Validated.sequence(getUriValues(instr, metaVocab.ssn.hasDeployment).map(getInstrumentDeployment(_, self)))
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
