package se.lu.nateko.cp.meta.services.upload

import java.time.LocalDate
import java.time.ZoneId

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.RDF

import scala.util.Try

import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.parseJsonStringArray
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.Validated


trait DobjMetaReader(val vocab: CpVocab) extends CpmetaReader:
	import TriplestoreConnection.*
	import RdfLens.{MetaConn, DocConn, DobjConn, GlobConn}

	def getSpecification(spec: IRI)(using DocConn): Validated[DataObjectSpec] =
		for
			self <- getLabeledResource(spec)
			projectUri <- getSingleUri(spec, metaVocab.hasAssociatedProject)
			project <- getProject(projectUri)
			dataThemeUri <- getSingleUri(spec, metaVocab.hasDataTheme)
			dataTheme <- getDataTheme(dataThemeUri)
			formatUri <- getSingleUri(spec, metaVocab.hasFormat)
			format <- getObjectFormat(formatUri)
			specificDatasetType <- getSpecDatasetType(spec)
			encoding <- getLabeledResource(spec, metaVocab.hasEncoding)
			dataLevel <- getSingleInt(spec, metaVocab.hasDataLevel)
			datasetSpecUri <- getOptionalUri(spec, metaVocab.containsDataset)
			datasetSpec <- datasetSpecUri.map(getDatasetSpec).sinkOption
			documentation <- getDocumentationObjs(spec)
			keywords <- getOptionalString(spec, metaVocab.hasKeywords)
		yield
			DataObjectSpec(
				self = self,
				project = project,
				theme = dataTheme,
				format = format,
				specificDatasetType = specificDatasetType,
				encoding = encoding,
				dataLevel = dataLevel,
				datasetSpec = datasetSpec,
				documentation = documentation,
				keywords = keywords.map(s => parseCommaSepList(s).toIndexedSeq)
			)

	def getObjSpecFormat(spec: IRI)(using MetaConn): Validated[IRI] =
		getSingleUri(spec, metaVocab.hasFormat)

	def getObjFormatForDobj(dobj: IRI)(using GlobConn): Validated[IRI] =
		getSingleUri(dobj, metaVocab.hasObjectSpec).flatMap(getObjSpecFormat)

	def getObjSubmitter(dobj: IRI): GlobConn ?=> Validated[IRI] =
		getSingleUri(dobj, metaVocab.wasSubmittedBy).flatMap: subm =>
			getSingleUri(subm, metaVocab.prov.wasAssociatedWith)

	private def getDatasetSpec(ds: IRI): MetaConn ?=> Validated[DatasetSpec] =
		for
			self <- getLabeledResource(ds)
			resolution <- getOptionalString(ds, metaVocab.hasTemporalResolution)
		yield
			DatasetSpec(
				self = self,
				resolution = resolution
			)

	def getSpecDatasetType(spec: IRI)(using MetaConn): Validated[DatasetType] =
		getSingleUri(spec, metaVocab.hasSpecificDatasetType).flatMap(getDatasetType)

	private def getDatasetType(iri: IRI): Validated[DatasetType] =
		if (iri === metaVocab.stationTimeSeriesDs) Validated.ok(DatasetType.StationTimeSeries)
		else if (iri === metaVocab.spatioTemporalDs) Validated.ok(DatasetType.SpatioTemporal)
		else Validated.error(s"URL $iri does not correspond to any of the expected dataset type instances")

	// only usable for doc objects associated to the metadata items (not colls or dobjs)
	private def getDocumentationObjs(item: IRI): DocConn ?=> Validated[Seq[PlainStaticObject]] =
		Validated.sequence(getUriValues(item, metaVocab.hasDocumentationObject).map(getPlainDocObject))

	def getOptionalStation(station: IRI): DocConn ?=> Validated[Option[Station]] =
		if hasStatement(station, metaVocab.hasStationId, null) then
			getStation(station).map(Some.apply)
		else Validated.ok(None)

	def getStation(stat: IRI): DocConn ?=> Validated[Station] =
		for
			org <- getOrganization(stat)
			id <- getSingleString(stat, metaVocab.hasStationId)
			locationOpt <- getStationLocation(stat, Some(org.name)).optional
			coverageUri <- getOptionalUri(stat, metaVocab.hasSpatialCoverage)
			coverage <- coverageUri.map(getCoverage[MetaConn]).sinkOption
			responsibleOrganizationUri <- getOptionalUri(stat, metaVocab.hasResponsibleOrganization)
			responsibleOrganization <- responsibleOrganizationUri.map(getOrganization).sinkOption
			specificInfo <- getStationSpecifics(stat)
			countryCode <- getOptionalString(stat, metaVocab.countryCode)
			funding <- getFundings(stat)
		yield
			Station(
				org = org,
				id = id,
				location = locationOpt,
				coverage = coverage,
				responsibleOrganization = responsibleOrganization,
				specificInfo = specificInfo,
				pictures = getUriLiteralValues(stat, metaVocab.hasDepiction),
				countryCode = countryCode.flatMap(CountryCode.unapply),
				funding = Option(funding).filterNot(_.isEmpty)
			)

	private def getStationSpecifics(stat: IRI): DocConn ?=> Validated[StationSpecifics] = mc ?=>
		if resourceHasType(stat, metaVocab.sites.stationClass) then
			for
				sites <- Validated.sequence(getUriValues(stat, metaVocab.operatesOn).map(getSite))
				ecosystems <- Validated.sequence:
					getUriValues(stat, metaVocab.hasEcosystemType).map(getLabeledResource)
				climateZoneUri <- getOptionalUri(stat, metaVocab.hasClimateZone)
				climateZone <- climateZoneUri.map(getLabeledResource).sinkOption
				meanAnnualTemp <- getOptionalFloat(stat, metaVocab.hasMeanAnnualTemp)
				meanAnnualPrecip <- getOptionalFloat(stat, metaVocab.hasMeanAnnualPrecip)
				operationalPeriod <- getOptionalString(stat, metaVocab.hasOperationalPeriod)
				discontinued <- getOptionalBool(stat, metaVocab.isDiscontinued)
				documentation <- getDocumentationObjs(stat)
			yield
				SitesStationSpecifics(
					sites = sites,
					ecosystems = ecosystems,
					climateZone = climateZone,
					meanAnnualTemp = meanAnnualTemp,
					meanAnnualPrecip = meanAnnualPrecip,
					operationalPeriod = operationalPeriod,
					discontinued = discontinued.getOrElse(false),
					documentation = documentation
				)
		else if resourceHasType(stat, metaVocab.ecoStationClass) then
			for
				icosSpecs <- getBasicIcosSpecifics(stat, vocab.etc)
				climateZoneUri <- getOptionalUri(stat, metaVocab.hasClimateZone)
				climateZone <- climateZoneUri.map(getLabeledResource).sinkOption
				ecosystemTypeUri <- getOptionalUri(stat, metaVocab.hasEcosystemType)
				ecosystemType <- ecosystemTypeUri.map(getLabeledResource).sinkOption
				meanAnnualTemp <- getOptionalFloat(stat, metaVocab.hasMeanAnnualTemp)
				meanAnnualPrecip <- getOptionalFloat(stat, metaVocab.hasMeanAnnualPrecip)
				meanAnnualRad <- getOptionalFloat(stat, metaVocab.hasMeanAnnualRadiation)
			yield
				EtcStationSpecifics(icosSpecs).copy(
					climateZone = climateZone,
					ecosystemType = ecosystemType,
					meanAnnualTemp = meanAnnualTemp,
					meanAnnualPrecip = meanAnnualPrecip,
					meanAnnualRad = meanAnnualRad,
					stationDocs = getUriLiteralValues(stat, metaVocab.hasDocumentationUri),
					stationPubs = getUriLiteralValues(stat, metaVocab.hasAssociatedPublication)
				)
		else if resourceHasType(stat, metaVocab.atmoStationClass) then
			for
				spec <- getBasicIcosSpecifics(stat, vocab.atc)
				wigosId <- getOptionalString(stat, metaVocab.hasWigosId)
			yield
				AtcStationSpecifics(spec, wigosId)
		else if resourceHasType(stat, metaVocab.oceStationClass) then
			getBasicIcosSpecifics(stat, vocab.otc)
		else if resourceHasType(stat, metaVocab.cityStationClass) then
			for timeZoneOffset <- getOptionalInt(stat, metaVocab.hasTimeZoneOffset)
			yield IcosCitiesStationSpecifics(timeZoneOffset = timeZoneOffset)
		else Validated.ok(NoStationSpecifics)
	end getStationSpecifics

	private def getBasicIcosSpecifics(stat: IRI, thematicCenter: IRI)(using DocConn): Validated[IcosStationSpecifics] =
		for
			lblDate <- getLabelingDate(stat)
			themeUri <- getOptionalUri(thematicCenter, metaVocab.hasDataTheme)
			theme <- themeUri.map(getDataTheme).sinkOption
			stationClass <- getOptionalString(stat, metaVocab.hasStationClass)
			timeZoneOffset <- getOptionalInt(stat, metaVocab.hasTimeZoneOffset)
			discontOpt <- getOptionalBool(stat, metaVocab.isDiscontinued)
			documentation <- getDocumentationObjs(stat)
		yield
			OtcStationSpecifics(
				theme = theme,
				stationClass = stationClass.map(IcosStationClass.valueOf),
				labelingDate = lblDate,
				discontinued = discontOpt.getOrElse(false),
				timeZoneOffset = timeZoneOffset,
				documentation = documentation
			)

	private def getLabelingDate(stat: IRI)(using conn: TSC): Validated[Option[LocalDate]] =
		//one-off local hack to avoid extensive config for fetching the labeling date from the labeling app metadata layer
		val vf = conn.factory

		val ctxts = Seq(
			"http://meta.icos-cp.eu/resources/stationentry/",
			"http://meta.icos-cp.eu/resources/stationlabeling/"
		).map(vf.createIRI)

		// overriding the graph view to the labeling graphs only
		given TSC = conn.withReadContexts(ctxts)

		val Seq(prodStLink, appStatus, statusDate) = Seq(
				"hasProductionCounterpart", "hasApplicationStatus", "hasAppStatusDate"
			)
			.map(vf.createIRI("http://meta.icos-cp.eu/ontologies/stationentry/", _))

		val labelingDate = getPropValueHolders(prodStLink, vocab.lit(stat.toJava))
			.filter: provSt =>
				hasStatement(provSt, appStatus, vf.createLiteral(CpVocab.LabeledStationStatus))
			.headOption
			.map: labeledSt =>
				getOptionalInstant(labeledSt, statusDate)
					.map(_.map(_.atZone(ZoneId.of("UTC")).toLocalDate))

		labelingDate.sinkOption.map(_.flatten)

	protected def getStationTimeSerMeta(
		dobj: IRI, vtLookup: VarMetaLookup, prod: Option[DataProduction], docConn: DocConn
	): DobjConn ?=> Validated[StationTimeSeriesMeta] = dobjConn ?=>
		val resV = for
			acqUri <- getSingleUri(dobj, metaVocab.wasAcquiredBy)
			instrumentsSeq <- Validated.sequence(getUriValues(acqUri, metaVocab.wasPerformedWith).map(getInstrumentLite))
			stationUri <- getSingleUri(acqUri, metaVocab.prov.wasAssociatedWith)
			station <- getStation(stationUri)(using docConn)
			siteUri <- getOptionalUri(acqUri, metaVocab.wasPerformedAt)
			site <- siteUri.map(getSite).sinkOption
			startOpt <- getOptionalInstant(acqUri, metaVocab.prov.startedAtTime)
			stopOpt <- getOptionalInstant(acqUri, metaVocab.prov.endedAtTime)
			samplingPointUri <- getOptionalUri(acqUri, metaVocab.hasSamplingPoint)
			samplingPoint <- samplingPointUri.map(getPosition).sinkOption
			samplingHeight <- getOptionalFloat(acqUri, metaVocab.hasSamplingHeight)
			nRows <- getOptionalInt(dobj, metaVocab.hasNumberOfRows)
			coverageUri <- getOptionalUri(dobj, metaVocab.hasSpatialCoverage)
			coverage <- coverageUri.map(getCoverage).sinkOption
			columnNames <- getOptionalString(dobj, metaVocab.hasActualColumnNames)
		yield
			val instrument = instrumentsSeq.toList match
				case Nil => None
				case single :: Nil => Some(Left(single))
				case many => Some(Right(many))

			val acqIntervalOpt = for start <- startOpt; stop <- stopOpt yield TimeInterval(start, stop)
			val acq = DataAcquisition(
				station = station,
				site = site,
				interval = acqIntervalOpt,
				instrument = instrument,
				samplingPoint = samplingPoint,
				samplingHeight = samplingHeight
			)
			val columnsOptV = columnNames.flatMap(parseJsonStringArray)
				.map:
					_.flatMap(vtLookup.lookup).toIndexedSeq
				.orElse: //if no actualColumnNames info is available, then all the plain mandatory columns have to be there
					Some(vtLookup.plainMandatory)
				.filter(_.nonEmpty)
				.map: columns =>
					acqIntervalOpt match
						case None => Validated.ok(columns)
						case Some(interval) =>
							addInstrDeplInfo(stationUri, interval, columns)
			columnsOptV.sinkOption.map: columnsOpt =>
				StationTimeSeriesMeta(acq, prod, nRows, coverage, columnsOpt)
		resV.flatMap(identity)
	end getStationTimeSerMeta

	private def addInstrDeplInfo(stationUri: IRI, acqInterval: TimeInterval, cols: Seq[VarMeta]): MetaConn ?=> Validated[Seq[VarMeta]] =
		val deploymentVs = getPropValueHolders(metaVocab.atOrganization, stationUri)
			.collect:
				case depl if hasStatement(depl, RDF.TYPE, metaVocab.ssn.deploymentClass) =>
					val instrs = getPropValueHolders(metaVocab.ssn.hasDeployment, depl).toList
					val instr = instrs match
						case Nil => Validated.error(s"No instruments for deployment $depl")
						case one :: Nil => Validated.ok(one)
						case many => Validated.error(s"Too many instruments for deployment $depl")
					instr.flatMap(getInstrumentDeployment(depl, _))
			.toIndexedSeq
		Validated.sequence(deploymentVs).map: deployments =>
			cols.map: vm =>
				val deps: Seq[InstrumentDeployment] = deployments.filter{dep =>
					dep.variableName.contains(vm.label) &&                //variable name matches
					dep.forProperty.exists(_.uri === vm.model.uri) &&        //variable metadata URI matches
					dep.start.fold(true)(start => start.isBefore(acqInterval.stop)) && //starts before data collection end
					dep.stop.fold(true)(stop => stop.isAfter(acqInterval.start))       //ends after data collection start
				}
				vm.copy(instrumentDeployments = Some(deps).filter(_.nonEmpty))
	end addInstrDeplInfo

	protected def getSpatioTempMeta(
		dobj: IRI, vtLookup: VarMetaLookup, prodOpt: Option[DataProduction]
	)(using dobjConn: DobjConn, docConn: DocConn): Validated[SpatioTemporalMeta] =

		val coverageV: Validated[GeoFeature] =
			for
				covIri <- getSingleUri(dobj, metaVocab.hasSpatialCoverage)(using dobjConn)
				cov0 <- getCoverage[DobjConn](covIri)
			yield
				val isCustomCoverage: Boolean = dobjConn.primaryContextView.hasStatement(covIri, RDF.TYPE, null)
				if isCustomCoverage then cov0.withOptUri(None) else cov0

		val prodV = new Validated(prodOpt)

		for
			title <- getSingleString[DobjConn](dobj, metaVocab.dcterms.title)
			description <- getOptionalString[DobjConn](dobj, metaVocab.dcterms.description)
			coverage <- coverageV
			temporal <- getTemporalCoverage(dobj)
			acqOpt <- getOptionalUri[DobjConn](dobj, metaVocab.wasAcquiredBy)
			stationOpt <- acqOpt.map(getOptionalUri[DobjConn](_, metaVocab.prov.wasAssociatedWith)).sinkOption
			station <- stationOpt.flatten.map(getStation).sinkOption
			samplingHeightOpt <- acqOpt.map(getOptionalFloat[DobjConn](_, metaVocab.hasSamplingHeight)).sinkOption
			prod <- prodV.require("Production info must be provided for a spatial data object")
			variables <- Validated.sequence(getUriValues[DobjConn](dobj, metaVocab.hasActualVariable).map(getL3VarInfo(_, vtLookup)))
		yield
			SpatioTemporalMeta(
				title = title,
				description = description,
				spatial = coverage,
				temporal = temporal,
				station = station,
				samplingHeight = samplingHeightOpt.flatten,
				productionInfo = prod,
				variables = Some(variables.flatten).filterNot(_.isEmpty)
			)

	protected def getDataProduction(obj: IRI, prod: IRI, docConn: DocConn): DobjConn ?=> Validated[DataProduction] =
		for
			creatorUri <- getSingleUri(prod, metaVocab.wasPerformedBy)
			creator <- getAgent(creatorUri)
			contributors <- Validated.sequence(getUriValues(prod, metaVocab.wasParticipatedInBy).map(getAgent))
			hostUri <- getOptionalUri(prod, metaVocab.wasHostedBy)
			host <- hostUri.map(getOrganization).sinkOption
			comment <- getOptionalString(prod, RDFS.COMMENT)
			sources <- Validated.sequence(getUriValues(obj, metaVocab.prov.hadPrimarySource)
				.map(getPlainDataObject(_)(using RdfLens.global(using docConn))))
			documentationUri <- getOptionalUri(prod, RDFS.SEEALSO)
			documentation <- documentationUri.map(getPlainDocObject(_)(using docConn)).sinkOption
			dateTime <- getSingleInstant(prod, metaVocab.hasEndTime)
		yield
			DataProduction(
				creator = creator,
				contributors = contributors,
				host = host,
				comment = comment,
				sources = sources,
				documentation = documentation,
				dateTime = dateTime
			)

	private def getFundings(stat: IRI): MetaConn ?=> Validated[Seq[Funding]] = Validated.sequence:
		getUriValues(stat, metaVocab.hasFunding).map: furi =>
			for
				self        <- getLabeledResource(furi)
				funderUri   <- getSingleUri(furi, metaVocab.hasFunder)
				funder      <- getFunder(funderUri)
				awardTitle  <- getOptionalString(furi, metaVocab.awardTitle)
				awardNumber <- getOptionalString(furi, metaVocab.awardNumber)
				awardUrl    <- getOptionalUriLiteral(furi, metaVocab.awardURI)
				start       <- getOptionalLocalDate(furi, metaVocab.hasStartDate)
				stop        <- getOptionalLocalDate(furi, metaVocab.hasEndDate)
			yield
				Funding(
					self = self,
					funder = funder,
					awardTitle = awardTitle,
					awardNumber = awardNumber,
					awardUrl = awardUrl,
					start = start,
					stop = stop
				)

	def getFunder(iri: IRI): MetaConn ?=> Validated[Funder] =
		for
			org <- getOrganization(iri)
			funder <- getOptionalString(iri, metaVocab.funderIdentifier)
			funderType <- getOptionalString(iri, metaVocab.funderIdentifierType)
		yield
			Funder(
				org = org,
				id = for
					idStr <- funder
					idTypeStr <- funderType
					idType <- Try(FunderIdType.valueOf(idTypeStr)).toOption
				yield idStr -> idType
			)

end DobjMetaReader
