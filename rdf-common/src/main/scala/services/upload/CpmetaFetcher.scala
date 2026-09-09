package se.lu.nateko.cp.meta.services.upload

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Value}
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import org.eclipse.rdf4j.rio.helpers.NTriplesUtil
import se.lu.nateko.cp.meta.api.{CloseableIterator, RdfLens, SparqlRunner}
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.{RdfStatement, StatementSource}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.utils.{Validated, containsEither, parseCommaSepList}

import java.net.URI

trait CpmetaReader:
	import StatementSource.*
	import RdfLens.{MetaConn, DobjConn, DocConn, ItemConn}

	val metaVocab: CpmetaVocab

	def getPlainDataObject(dobj: IRI)(using DobjConn): Validated[PlainStaticObject] =
		getPlainStaticObject(dobj)

	def getPlainDocObject(dobj: IRI)(using DocConn): Validated[PlainStaticObject] =
		getPlainStaticObject(dobj)

	private def getPlainStaticObject(dobj: IRI)(using StatementSource): Validated[PlainStaticObject] =
		val properties = SubjectStatements(dobj)
		for
			hashsum <- getHashsum(dobj, metaVocab.hasSha256sum)(using properties)
			fileName <- getOptionalString(dobj, metaVocab.dcterms.title)(using properties).flatMap:
				case None => getSingleString(dobj, metaVocab.hasName)(using properties)
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
		val properties = SubjectStatements(subm)
		for
			submitterUri <- getSingleUri(subm, metaVocab.prov.wasAssociatedWith)(using properties)
			submitter <- getOrganization(submitterUri)
			start <- getSingleInstant(subm, metaVocab.prov.startedAtTime)(using properties)
			stop <- getOptionalInstant(subm, metaVocab.prov.endedAtTime)(using properties)
		yield
			DataSubmission(
				submitter = submitter,
				start = start,
				stop = stop
			)

	def getAgent(uri: IRI): MetaConn ?=> Validated[Agent] =
		getOptionalString(uri, metaVocab.hasFirstName).flatMap: a =>
			if a.isDefined then
				getPerson(uri)
			else
				getOrganization(uri)

	def getOrganization(org: IRI): MetaConn ?=> Validated[Organization] =
		val properties = SubjectStatements(org)
		for
			self <- getLabeledResource(org)(using properties)
			name <- getSingleString(org, metaVocab.hasName)(using properties)
			emailOpt <- getOptionalString(org, metaVocab.hasEmail)(using properties)
			websiteOpt <- getOptionalUri(org, RDFS.SEEALSO)(using properties)
			webpageUriOpt <- getOptionalUri(org, metaVocab.hasWebpageElements)(using properties)
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

	def getPerson(pers: IRI): StatementSource ?=> Validated[Person] =
		val properties = SubjectStatements(pers)
		for
			self <- getLabeledResource(pers)(using properties)
			firstName <- getSingleString(pers, metaVocab.hasFirstName)(using properties)
			lastName <- getSingleString(pers, metaVocab.hasLastName)(using properties)
			emailOpt <- getOptionalString(pers, metaVocab.hasEmail)(using properties)
			orcidOpt <- getOptionalString(pers, metaVocab.hasOrcidId)(using properties)
		yield
			Person(
				self = self,
				firstName = firstName,
				lastName = lastName,
				email = emailOpt,
				orcid = orcidOpt.flatMap(Orcid.unapply)
			)

	def getProject(project: IRI): MetaConn ?=> Validated[Project] =
		val properties = SubjectStatements(project)
		for
			self <- getLabeledResource(project)(using properties)
			keywordsOpt <- getOptionalString(project, metaVocab.hasKeywords)(using properties)
		yield
			Project(
				self = self,
				keywords = keywordsOpt.map(s => parseCommaSepList(s).toIndexedSeq)
			)

	def getObjectFormat(format: IRI): MetaConn ?=> Validated[ObjectFormat] =
		val properties = SubjectStatements(format)
		for
			self <- getLabeledResource(format)(using properties)
		yield
			ObjectFormat(
				self = self,
				goodFlagValues = Some(getStringValues(format, metaVocab.hasGoodFlagValue)(using properties)).filterNot(_.isEmpty)
			)

	def getDataTheme(theme: IRI): MetaConn ?=> Validated[DataTheme] =
		val properties = SubjectStatements(theme)
		for
			self <- getLabeledResource(theme)(using properties)
			icon <- getSingleUriLiteral(theme, metaVocab.hasIcon)(using properties)
			markerIconOpt <- getOptionalUriLiteral(theme, metaVocab.hasMarkerIcon)(using properties)
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

	private def isUnderMoratorium(item: IRI)(using ItemConn): Boolean =
		getSingleUri(item, metaVocab.wasSubmittedBy)
			.flatMap(getSubmission)
			.result
			.map(_.isUnderMoratorium)
			.getOrElse(false)

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
		def latest(item: IRI, seen: Set[IRI]): Seq[IRI] =
			val nextVersions = getNextVersions(item).flatMap: next =>
				if seen.contains(next)
				then Nil
				else latest(next, seen + next)
			if nextVersions.isEmpty then Seq(item) else nextVersions
		latest(item, Set.empty).map(_.toJava) match
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
		getValTypeLookupFrom(datasetSpec)

	/**
	 * Fetches all variable/column metadata needed by [[getValTypeLookup]] in one query.
	 * This is intended for remote connections, where property-at-a-time reads are HTTP requests.
	 */
	def getValTypeLookupBatched(datasetSpec: IRI)(using conn: MetaConn, sparql: SparqlRunner): Validated[VarMetaLookup] =
		Validated:
			sparql.evaluateGraphQuery(datasetVariablesQuery(datasetSpec, conn.readContexts))
				.map(RdfStatement.fromRdf4jStatement)
				.toIndexedSeq
		.flatMap: statements =>
			given StatementSource = InMemoryStatementSource(statements)
			getValTypeLookupFrom(datasetSpec)

	private def getValTypeLookupFrom(datasetSpec: IRI)(using StatementSource): Validated[VarMetaLookup] =
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
		getValueTypeFrom(vt)

	private def getValueTypeFrom(vt: IRI)(using StatementSource): Validated[ValueType] =
		for
			labeledResource <- getLabeledResource(vt)
			quantityKindUri <- getOptionalUri(vt, metaVocab.hasQuantityKind)
			quantityKind <- quantityKindUri.map(getLabeledResource[StatementSource]).sinkOption
			unit <- getOptionalString(vt, metaVocab.hasUnit)
		yield
			ValueType(labeledResource, quantityKind, unit)

	private def getDatasetVars(ds: IRI)(using StatementSource): Validated[Seq[DatasetVariable]] =
		import metaVocab.*
		getDatasetVarsOrCols(ds, hasVariable, hasVariableTitle, isRegexVariable, isOptionalVariable)

	private def getDatasetColumns(ds: IRI)(using StatementSource): Validated[Seq[DatasetVariable]] =
		import metaVocab.*
		getDatasetVarsOrCols(ds, hasColumn, hasColumnTitle, isRegexColumn, isOptionalColumn)

	private def getDatasetVarsOrCols(
		ds: IRI, varProp: IRI, titleProp: IRI, regexProp: IRI, optProp: IRI
	)(using StatementSource): Validated[Seq[DatasetVariable]] =
		Validated.sequence(getUriValues(ds, varProp).map: dv =>
			for
				self <- getLabeledResource(dv)
				title <- getSingleString(dv, titleProp)
				valueTypeUri <- getSingleUri(dv, metaVocab.hasValueType)
				valueType <- getValueTypeFrom(valueTypeUri)
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

	private[upload] def datasetVariablesQuery(datasetSpec: IRI, readContexts: Seq[IRI]): String =
		import metaVocab.*
		def iri(value: IRI): String = NTriplesUtil.toNTriplesString(value)
		def values(values: IRI*): String = values.map(iri).mkString(" ")
		val from = readContexts.distinct.map(context => s"FROM ${iri(context)}").mkString("\n")

		s"""CONSTRUCT {
			|  ${iri(datasetSpec)} ?membership ?variable .
			|  ?variable ?variableProperty ?variableValue .
			|  ?valueType ?valueTypeProperty ?valueTypeValue .
			|  ?quantityKind ?quantityKindProperty ?quantityKindValue .
			|}
			|$from
			|WHERE {
			|  VALUES ?membership { ${values(hasVariable, hasColumn)} }
			|  ${iri(datasetSpec)} ?membership ?variable .
			|  OPTIONAL {
			|    {
			|      VALUES ?variableProperty { ${values(RDFS.LABEL, RDFS.COMMENT, hasVariableTitle, hasColumnTitle, hasValueType, hasValueFormat, isRegexVariable, isRegexColumn, isOptionalVariable, isOptionalColumn, isQualityFlagFor)} }
			|      ?variable ?variableProperty ?variableValue .
			|    } UNION {
			|      ?variable ${iri(hasValueType)} ?valueType .
			|      VALUES ?valueTypeProperty { ${values(RDFS.LABEL, RDFS.COMMENT, hasQuantityKind, hasUnit)} }
			|      ?valueType ?valueTypeProperty ?valueTypeValue .
			|    } UNION {
			|      ?variable ${iri(hasValueType)} ?valueType .
			|      ?valueType ${iri(hasQuantityKind)} ?quantityKind .
			|      VALUES ?quantityKindProperty { ${values(RDFS.LABEL, RDFS.COMMENT)} }
			|      ?quantityKind ?quantityKindProperty ?quantityKindValue .
			|    }
			|  }
			|}""".stripMargin

	private final class InMemoryStatementSource(statements: IndexedSeq[RdfStatement]) extends StatementSource:
		override def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[RdfStatement] =
			val matching = statements.iterator.filter: statement =>
				(subject == null || statement.subject == subject) &&
				(predicate == null || statement.predicate == predicate) &&
				(obj == null || statement.obj == obj)
			new CloseableIterator.Wrap(matching, () => ())

		override def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
			statements.exists: statement =>
				(subject == null || statement.subject == subject) &&
				(predicate == null || statement.predicate == predicate) &&
				(obj == null || statement.obj == obj)

	def getInstrumentLite(instr: IRI): MetaConn ?=> Validated[UriResource] =
		val modelValid = getOptionalString(instr, metaVocab.hasModel).map(model => model.filter(_ != CpmetaVocab.defaultInstrModel))
		val serialNumberValid = getOptionalString(instr, metaVocab.hasSerialNumber).map(serialNumber => serialNumber.filter(_ != CpmetaVocab.defaultSerialNum))

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

	def getInstrumentDeployment(iri: IRI, instrument: IRI): MetaConn ?=> Validated[InstrumentDeployment] =
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

	def getInstrument(instr: IRI): MetaConn ?=> Validated[Instrument] =
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
				partOf <- getPropValueHolders(metaVocab.hasInstrumentComponent, instr)
					.map(getInstrumentLite).headOption.sinkOption
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
