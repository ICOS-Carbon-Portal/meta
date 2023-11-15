package se.lu.nateko.cp.meta.services.upload

import java.time.LocalDate
import java.time.ZoneId

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.RDF

import scala.util.Try

import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.parseJsonStringArray
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.instanceserver.InstanceServerUtils
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.Validated

trait DobjMetaFetcher extends CpmetaFetcher{

	def plainObjFetcher: PlainStaticObjectFetcher
	protected def vocab: CpVocab

	def getSpecification(spec: IRI) = DataObjectSpec(
		self = getLabeledResource(spec),
		project = getProject(getSingleUri(spec, metaVocab.hasAssociatedProject)),
		theme = getDataTheme(getSingleUri(spec, metaVocab.hasDataTheme)),
		format = getObjectFormat(getSingleUri(spec, metaVocab.hasFormat)),
		specificDatasetType = getDatasetType(getSingleUri(spec, metaVocab.hasSpecificDatasetType)),
		encoding = getLabeledResource(spec, metaVocab.hasEncoding),
		dataLevel = getSingleInt(spec, metaVocab.hasDataLevel),
		datasetSpec = getOptionalUri(spec, metaVocab.containsDataset).map(getDatasetSpec),
		documentation = getDocumentationObjs(spec),
		keywords = getOptionalString(spec, metaVocab.hasKeywords).map(s => parseCommaSepList(s).toIndexedSeq)
	)

	private def getDatasetSpec(ds: IRI) = DatasetSpec(
		self = getLabeledResource(ds),
		resolution = getOptionalString(ds, metaVocab.hasTemporalResolution)
	)

	private def getDatasetType(iri: IRI): DatasetType =
		if (iri === metaVocab.stationTimeSeriesDs) DatasetType.StationTimeSeries
		else if (iri === metaVocab.spatioTemporalDs) DatasetType.SpatioTemporal
		else throw new MetadataException(s"URL $iri does not correspond to any of the expected dataset type instances")


	private def getDocumentationObjs(item: IRI): Seq[PlainStaticObject] =
		server.getUriValues(item, metaVocab.hasDocumentationObject).map(plainObjFetcher.getPlainStaticObject)

	def getOptionalStation(station: IRI): Try[Option[Station]] = Try{
		if(server.hasStatement(Some(station), Some(metaVocab.hasStationId), None))
			Some(getStation(station))
		else None
	}

	protected  def getStation(stat: IRI) = {
		val org = getOrganization(stat)
		Station(
			org = org,
			id = getSingleString(stat, metaVocab.hasStationId),
			location = getStationLocation(stat, Some(org.name)),
			coverage = getOptionalUri(stat, metaVocab.hasSpatialCoverage).map(getCoverage),
			responsibleOrganization = getOptionalUri(stat, metaVocab.hasResponsibleOrganization).map(getOrganization),
			specificInfo = getStationSpecifics(stat),
			pictures = server.getUriLiteralValues(stat, metaVocab.hasDepiction),
			countryCode = getOptionalString(stat, metaVocab.countryCode).flatMap(CountryCode.unapply),
			funding = Option(getFundings(stat)).filterNot(_.isEmpty)
		)
	}

	private def getStationSpecifics(stat: IRI): StationSpecifics = {
		if(server.resourceHasType(stat, metaVocab.sites.stationClass))
			SitesStationSpecifics(
				sites = server.getUriValues(stat, metaVocab.operatesOn).map(getSite),
				ecosystems = server.getUriValues(stat, metaVocab.hasEcosystemType).map(getLabeledResource),
				climateZone = getOptionalUri(stat, metaVocab.hasClimateZone).map(getLabeledResource),
				meanAnnualTemp = getOptionalFloat(stat, metaVocab.hasMeanAnnualTemp),
				operationalPeriod = getOptionalString(stat, metaVocab.hasOperationalPeriod),
				discontinued = getOptionalBool(stat, metaVocab.isDiscontinued).getOrElse(false),
				documentation = getDocumentationObjs(stat)
			)
		else if(server.resourceHasType(stat, metaVocab.ecoStationClass))
			EtcStationSpecifics(getBasicIcosSpecifics(stat, vocab.etc)).copy(
				climateZone = getOptionalUri(stat, metaVocab.hasClimateZone).map(getLabeledResource),
				ecosystemType = getOptionalUri(stat, metaVocab.hasEcosystemType).map(getLabeledResource),
				meanAnnualTemp = getOptionalFloat(stat, metaVocab.hasMeanAnnualTemp),
				meanAnnualPrecip = getOptionalFloat(stat, metaVocab.hasMeanAnnualPrecip),
				meanAnnualRad = getOptionalFloat(stat, metaVocab.hasMeanAnnualRadiation),
				stationDocs = server.getUriLiteralValues(stat, metaVocab.hasDocumentationUri),
				stationPubs = server.getUriLiteralValues(stat, metaVocab.hasAssociatedPublication)
			)
		else if(server.resourceHasType(stat, metaVocab.atmoStationClass))
			AtcStationSpecifics(
				getBasicIcosSpecifics(stat, vocab.atc),
				getOptionalString(stat, metaVocab.hasWigosId)
			)
		else if(server.resourceHasType(stat, metaVocab.oceStationClass))
			getBasicIcosSpecifics(stat, vocab.otc)

		else if(server.resourceHasType(stat, metaVocab.cityStationClass))
			IcosCitiesStationSpecifics(timeZoneOffset = getOptionalInt(stat, metaVocab.hasTimeZoneOffset))
		else NoStationSpecifics
	}

	private def getBasicIcosSpecifics(stat: IRI, thematicCenter: IRI): IcosStationSpecifics = {
		val (lblDate, discont) = getLabelingDateAndDiscontinuation(stat)
		OtcStationSpecifics(
			theme = getOptionalUri(thematicCenter, metaVocab.hasDataTheme).map(getDataTheme),
			stationClass = getOptionalString(stat, metaVocab.hasStationClass).map(IcosStationClass.valueOf),
			labelingDate = lblDate,
			discontinued = discont,
			timeZoneOffset = getOptionalInt(stat, metaVocab.hasTimeZoneOffset),
			documentation = getDocumentationObjs(stat)
		)
	}

	private def getLabelingDateAndDiscontinuation(stat: IRI): (Option[LocalDate], Boolean) = {
		//one-off local hack to avoid extensive config for fetching the labeling date from the labeling app metadata layer
		val vf = server.factory

		val ctxts = Seq(
			"http://meta.icos-cp.eu/resources/stationentry/",
			"http://meta.icos-cp.eu/resources/stationlabeling/"
		).map(vf.createIRI)

		val lblServer = server.withContexts(ctxts, Nil)

		val Seq(prodStLink, appStatus, statusDate, stationId) = Seq(
				"hasProductionCounterpart", "hasApplicationStatus", "hasAppStatusDate", "hasShortName"
			)
			.map(vf.createIRI("http://meta.icos-cp.eu/ontologies/stationentry/", _))

		val provStOpt: Option[IRI] = lblServer
			.getStatements(None, Some(prodStLink), Some(vocab.lit(stat.toJava)))
			.toIndexedSeq
			.collect{
				case Rdf4jStatement(provSt, _, _) if lblServer.hasStatement(Some(provSt), Some(appStatus), None) => provSt
			}
			.headOption

		val labelingDate = provStOpt
			.filter{ provSt => lblServer
				.hasStatement(provSt, appStatus, vf.createLiteral(CpVocab.LabeledStationStatus))
			}
			.flatMap{labeledSt => FetchingHelper(lblServer)
				.getOptionalInstant(labeledSt, statusDate)
				.map(_.atZone(ZoneId.of("UTC")).toLocalDate)
			}

		val discontinued: Boolean = provStOpt.fold(true){provSt =>
			!lblServer.hasStatement(Some(provSt), Some(stationId), None)
		}

		labelingDate -> discontinued
	}

	protected def getStationTimeSerMeta(dobj: IRI, vtLookup: VarMetaLookup, prod: Option[DataProduction]): StationTimeSeriesMeta =
		val vf = server.factory
		val acqUri = getSingleUri(dobj, metaVocab.wasAcquiredBy)
		val instrumentRefs = server.getUriValues(acqUri, metaVocab.wasPerformedWith)

		val instrument = instrumentRefs.map(getInstrumentLite).toList match{
				case Nil => None
				case single :: Nil => Some(Left(single))
				case many => Some(Right(many))
			}

		val stationUri = getSingleUri(acqUri, metaVocab.prov.wasAssociatedWith)

		val acq = DataAcquisition(
			station = getStation(stationUri),
			site = getOptionalUri(acqUri, metaVocab.wasPerformedAt).map(getSite),
			interval = for(
				start <- getOptionalInstant(acqUri, metaVocab.prov.startedAtTime);
				stop <- getOptionalInstant(acqUri, metaVocab.prov.endedAtTime)
			) yield TimeInterval(start, stop),
			instrument = instrument,
			samplingPoint = getOptionalUri(acqUri, metaVocab.hasSamplingPoint).flatMap(getPosition),
			samplingHeight = getOptionalFloat(acqUri, metaVocab.hasSamplingHeight)
		)

		val deployments = server
			.getStatements(None, Some(metaVocab.atOrganization), Some(stationUri))
			.toIndexedSeq
			.collect:
				case Rdf4jStatement(subj, _, _) if server.hasStatement(subj, RDF.TYPE, metaVocab.ssn.deploymentClass) =>
					val instrs = server
						.getStatements(None, Some(metaVocab.ssn.hasDeployment), Some(subj))
						.collect:
							case Rdf4jStatement(instr, _, _) => instr
						.toList
					val instr = instrs match
						case Nil => throw new Exception(s"No instruments for deployment $subj")
						case one :: Nil => one
						case many => throw new Exception(s"Too many instruments for deployment $subj")
					getInstrumentDeployment(subj, instr)
			.toIndexedSeq

		val nRows = getOptionalInt(dobj, metaVocab.hasNumberOfRows)

		val coverage = getOptionalUri(dobj, metaVocab.hasSpatialCoverage).map(getCoverage)

		val columns = getOptionalString(dobj, metaVocab.hasActualColumnNames).flatMap(parseJsonStringArray)
			.map{
				_.flatMap(vtLookup.lookup).toIndexedSeq
			}.orElse{ //if no actualColumnNames info is available, then all the plain mandatory columns have to be there
				Some(vtLookup.plainMandatory)
			}.filter(_.nonEmpty)

		val columnsWithDeployments: Option[Seq[VarMeta]] = columns.map{
			_.map{vm =>
				val deps: Seq[InstrumentDeployment] = deployments.filter{dep =>
					dep.variableName.contains(vm.label) &&                //variable name matches
					dep.forProperty.exists(_.uri === vm.model.uri) &&        //variable metadata URI matches
					acq.interval.fold(false){ti =>
						dep.start.fold(true)(start => start.isBefore(ti.stop)) && //starts before data collection end
						dep.stop.fold(true)(stop => stop.isAfter(ti.start))       //ends after data collection start
					}
				}
				vm.copy(instrumentDeployments = Some(deps).filter(_.nonEmpty))
			}
		}

		StationTimeSeriesMeta(acq, prod, nRows, coverage, columnsWithDeployments)
	end getStationTimeSerMeta

	protected def getSpatioTempMeta(dobj: IRI, vtLookup: VarMetaLookup, prodOpt: Option[DataProduction]): SpatioTemporalMeta =

		val coverage: GeoFeature =
			val covIri = getSingleUri(dobj, metaVocab.hasSpatialCoverage)
			val cov0 = getCoverage(covIri)
			val isCustomCoverage: Boolean = server.writeContextsView.hasStatement(Some(covIri), Some(RDF.TYPE), None)
			if isCustomCoverage then cov0.withOptUri(None) else cov0

		assert(prodOpt.isDefined, "Production info must be provided for a spatial data object")
		val prod = prodOpt.get

		val acqOpt = getOptionalUri(dobj, metaVocab.wasAcquiredBy)
		val stationOpt = acqOpt.flatMap(getOptionalUri(_, metaVocab.prov.wasAssociatedWith))

		SpatioTemporalMeta(
			title = getSingleString(dobj, metaVocab.dcterms.title),
			description = getOptionalString(dobj, metaVocab.dcterms.description),
			spatial = coverage,
			temporal = getTemporalCoverage(dobj),
			station = stationOpt.map(getStation),
			samplingHeight = acqOpt.flatMap(getOptionalFloat(_, metaVocab.hasSamplingHeight)),
			productionInfo = prod,
			variables = Some(
				server.getUriValues(dobj, metaVocab.hasActualVariable).flatMap(getL3VarInfo(_, vtLookup))
			).filter(_.nonEmpty)
		)


	protected def getDataProduction(obj: IRI, prod: IRI) = DataProduction(
		creator = getAgent(getSingleUri(prod, metaVocab.wasPerformedBy)),
		contributors = server.getUriValues(prod, metaVocab.wasParticipatedInBy).map(getAgent),
		host = getOptionalUri(prod, metaVocab.wasHostedBy).map(getOrganization),
		comment = getOptionalString(prod, RDFS.COMMENT),
		sources = server.getUriValues(obj, metaVocab.prov.hadPrimarySource).map(plainObjFetcher.getPlainStaticObject),
		documentation = getOptionalUri(prod, RDFS.SEEALSO).map(plainObjFetcher.getPlainStaticObject),
		dateTime = getSingleInstant(prod, metaVocab.hasEndTime)
	)

	private def getFundings(stat: IRI): Seq[Funding] =
		server.getUriValues(stat, metaVocab.hasFunding).map{furi =>
			val funderUri = getSingleUri(furi, metaVocab.hasFunder)
			Funding(
				self = getLabeledResource(furi),
				funder = getFunder(funderUri),
				awardTitle = getOptionalString(furi, metaVocab.awardTitle),
				awardNumber = getOptionalString(furi, metaVocab.awardNumber),
				awardUrl = getOptionalUriLiteral(furi, metaVocab.awardURI),
				start = getOptionalLocalDate(furi, metaVocab.hasStartDate),
				stop = getOptionalLocalDate(furi, metaVocab.hasEndDate)
			)
		}

	protected def getFunder(iri: IRI) = Funder(
		org = getOrganization(iri),
		id = for(
			idStr <- getOptionalString(iri, metaVocab.funderIdentifier);
			idTypeStr <- getOptionalString(iri, metaVocab.funderIdentifierType);
			idType <- Try(FunderIdType.valueOf(idTypeStr)).toOption
		) yield idStr -> idType
	)

}

class DobjMetaReader(documentsGraph: IRI, vocab: CpVocab, metaVocab: CpmetaVocab) extends CpmetaReader(metaVocab):
	import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*

	def getDocument(dobj: IRI): TSC2V[PlainStaticObject] = conn ?=>
		getPlainStaticObject(dobj)(using conn.withReadContexts(Seq(documentsGraph)))

	def getSpecification(spec: IRI): TSC2V[DataObjectSpec] =
		for
			self <- getLabeledResource(spec)
			projectUri <- getSingleUri(spec, metaVocab.hasAssociatedProject)
			project <- getProject(projectUri)
			dataThemeUri <- getSingleUri(spec, metaVocab.hasDataTheme)
			dataTheme <- getDataTheme(dataThemeUri)
			formatUri <- getSingleUri(spec, metaVocab.hasFormat)
			format <- getObjectFormat(formatUri)
			specificDatasetTypeUri <- getSingleUri(spec, metaVocab.hasSpecificDatasetType)
			specificDatasetType <- getDatasetType(specificDatasetTypeUri)
			encoding <- getLabeledResource(spec, metaVocab.hasEncoding)
			dataLevel <- getSingleInt(spec, metaVocab.hasDataLevel)
			datasetSpecUri <- getOptionalUri(spec, metaVocab.containsDataset)
			datasetSpec <- Validated.sinkOption(datasetSpecUri.map(getDatasetSpec))
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

	private def getDatasetSpec(ds: IRI): TSC2V[DatasetSpec] =
		for
			self <- getLabeledResource(ds)
			resolution <- getOptionalString(ds, metaVocab.hasTemporalResolution)
		yield
			DatasetSpec(
				self = self,
				resolution = resolution
			)

	private def getDatasetType(iri: IRI): TSC2V[DatasetType] =
		if (iri === metaVocab.stationTimeSeriesDs) Validated.ok(DatasetType.StationTimeSeries)
		else if (iri === metaVocab.spatioTemporalDs) Validated.ok(DatasetType.SpatioTemporal)
		else Validated.error(s"URL $iri does not correspond to any of the expected dataset type instances")

	private def getDocumentationObjs(item: IRI): TSC2V[Seq[PlainStaticObject]] =
		Validated.sequence(getUriValues(item, metaVocab.hasDocumentationObject).map(getPlainStaticObject))

	def getOptionalStation(station: IRI): TSC2V[Station] =
		if hasStatement(Some(station), Some(metaVocab.hasStationId), None) then
			getStation(station)
		else Validated.empty

	def getStation(stat: IRI): TSC2V[Station] =
		for
			org <- getOrganization(stat)
			id <- getSingleString(stat, metaVocab.hasStationId)
			location <- getStationLocation(stat, Some(org.name))
			coverageUri <- getOptionalUri(stat, metaVocab.hasSpatialCoverage)
			coverage <- Validated.sinkOption(coverageUri.map(getCoverage))
			responsibleOrganizationUri <- getOptionalUri(stat, metaVocab.hasResponsibleOrganization)
			responsibleOrganization <- Validated.sinkOption(responsibleOrganizationUri.map(getOrganization))
			specificInfo <- getStationSpecifics(stat)
			countryCode <- getOptionalString(stat, metaVocab.countryCode)
			funding <- getFundings(stat)
		yield
			Station(
				org = org,
				id = id,
				location = Some(location),
				coverage = coverage,
				responsibleOrganization = responsibleOrganization,
				specificInfo = specificInfo,
				pictures = getUriLiteralValues(stat, metaVocab.hasDepiction),
				countryCode = countryCode.flatMap(CountryCode.unapply),
				funding = Option(funding).filterNot(_.isEmpty)
			)

	private def getStationSpecifics(stat: IRI): TSC2V[StationSpecifics] =
		if resourceHasType(stat, metaVocab.sites.stationClass) then
			for
				sites <- Validated.sequence(getUriValues(stat, metaVocab.operatesOn).map(getSite))
				ecosystems <- Validated.sequence(getUriValues(stat, metaVocab.hasEcosystemType).map(getLabeledResource))
				climateZoneUri <- getOptionalUri(stat, metaVocab.hasClimateZone)
				climateZone <- Validated.sinkOption(climateZoneUri.map(getLabeledResource))
				meanAnnualTemp <- getOptionalFloat(stat, metaVocab.hasMeanAnnualTemp)
				operationalPeriod <- getOptionalString(stat, metaVocab.hasOperationalPeriod)
				discontinued <- getOptionalBool(stat, metaVocab.isDiscontinued)
				documentation <- getDocumentationObjs(stat)
			yield
				SitesStationSpecifics(
					sites = sites,
					ecosystems = ecosystems,
					climateZone = climateZone,
					meanAnnualTemp = meanAnnualTemp,
					operationalPeriod = operationalPeriod,
					discontinued = discontinued.getOrElse(false),
					documentation = documentation
				)
		else if resourceHasType(stat, metaVocab.ecoStationClass) then
			for
				icosSpecs <- getBasicIcosSpecifics(stat, vocab.etc)
				climateZoneUri <- getOptionalUri(stat, metaVocab.hasClimateZone)
				climateZone <- Validated.sinkOption(climateZoneUri.map(getLabeledResource))
				ecosystemTypeUri <- getOptionalUri(stat, metaVocab.hasEcosystemType)
				ecosystemType <- Validated.sinkOption(ecosystemTypeUri.map(getLabeledResource))
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

	private def getBasicIcosSpecifics(stat: IRI, thematicCenter: IRI): TSC2V[IcosStationSpecifics] =
		for
			(lblDate, discont) <- getLabelingDateAndDiscontinuation(stat)
			themeUri <- getOptionalUri(thematicCenter, metaVocab.hasDataTheme)
			theme <- Validated.sinkOption(themeUri.map(getDataTheme))
			stationClass <- getOptionalString(stat, metaVocab.hasStationClass)
			timeZoneOffset <- getOptionalInt(stat, metaVocab.hasTimeZoneOffset)
			documentation <- getDocumentationObjs(stat)
		yield
			OtcStationSpecifics(
				theme = theme,
				stationClass = stationClass.map(IcosStationClass.valueOf),
				labelingDate = lblDate,
				discontinued = discont,
				timeZoneOffset = timeZoneOffset,
				documentation = documentation
			)

	private def getLabelingDateAndDiscontinuation(stat: IRI): TSC2V[(Option[LocalDate], Boolean)] = conn ?=>
		//one-off local hack to avoid extensive config for fetching the labeling date from the labeling app metadata layer
		val vf = conn.factory

		val ctxts = Seq(
			"http://meta.icos-cp.eu/resources/stationentry/",
			"http://meta.icos-cp.eu/resources/stationlabeling/"
		).map(vf.createIRI)

		val lblConn = conn.withReadContexts(ctxts)

		val Seq(prodStLink, appStatus, statusDate, stationId) = Seq(
				"hasProductionCounterpart", "hasApplicationStatus", "hasAppStatusDate", "hasShortName"
			)
			.map(vf.createIRI("http://meta.icos-cp.eu/ontologies/stationentry/", _))

		val provStOpt: Option[IRI] = lblConn
			.getStatements(None, Some(prodStLink), Some(vocab.lit(stat.toJava)))
			.toIndexedSeq
			.collect{
				case Rdf4jStatement(provSt, _, _) if lblConn.hasStatement(Some(provSt), Some(appStatus), None) => provSt
			}
			.headOption

		val labelingDate = provStOpt
			.filter{ provSt => lblConn
				.hasStatement(provSt, appStatus, vf.createLiteral(CpVocab.LabeledStationStatus))
			}
			.map{labeledSt => getOptionalInstant(labeledSt, statusDate)
				.map(_.map(_.atZone(ZoneId.of("UTC")).toLocalDate))
			}

		val discontinued: Boolean = provStOpt.fold(true){provSt =>
			!lblConn.hasStatement(Some(provSt), Some(stationId), None)
		}

		Validated.sinkOption(labelingDate).map(_.flatten -> discontinued)

	protected def getStationTimeSerMeta(dobj: IRI, vtLookup: VarMetaLookup, prod: Option[DataProduction]): TSC2V[StationTimeSeriesMeta] = conn ?=>
		val vf = conn.factory

		val stationTimeSeriesMeta = for
			acqUri <- getSingleUri(dobj, metaVocab.wasAcquiredBy)
			instrumentsSeq <- Validated.sequence(getUriValues(acqUri, metaVocab.wasPerformedWith).map(getInstrumentLite))
			stationUri <- getSingleUri(acqUri, metaVocab.prov.wasAssociatedWith)
			station <- getStation(stationUri)
			siteUri <- getOptionalUri(acqUri, metaVocab.wasPerformedAt)
			site <- Validated.sinkOption(siteUri.map(getSite))
			startOpt <- getOptionalInstant(acqUri, metaVocab.prov.startedAtTime)
			stopOpt <- getOptionalInstant(acqUri, metaVocab.prov.endedAtTime)
			samplingPointUri <- getOptionalUri(acqUri, metaVocab.hasSamplingPoint)
			samplingPoint <- Validated.sinkOption(samplingPointUri.map(getPosition))
			samplingHeight <- getOptionalFloat(acqUri, metaVocab.hasSamplingHeight)
			nRows <- getOptionalInt(dobj, metaVocab.hasNumberOfRows)
			coverageUri <- getOptionalUri(dobj, metaVocab.hasSpatialCoverage)
			coverage <- Validated.sinkOption(coverageUri.map(getCoverage))
			columnNames <- getOptionalString(dobj, metaVocab.hasActualColumnNames)
		yield
			val instrument = instrumentsSeq.toList match
				case Nil => None
				case single :: Nil => Some(Left(single))
				case many => Some(Right(many))

			val acq = DataAcquisition(
				station = station,
				site = site,
				interval = for
					start <- startOpt
					stop <- stopOpt
				yield TimeInterval(start, stop),
				instrument = instrument,
				samplingPoint = samplingPoint,
				samplingHeight = samplingHeight
			)

			val deploymentsV = Validated.sequence(getStatements(None, Some(metaVocab.atOrganization), Some(stationUri))
				.toIndexedSeq
				.collect:
					case Rdf4jStatement(subj, _, _) if hasStatement(subj, RDF.TYPE, metaVocab.ssn.deploymentClass) =>
						val instrs = getStatements(None, Some(metaVocab.ssn.hasDeployment), Some(subj))
							.collect:
								case Rdf4jStatement(instr, _, _) => instr
							.toList
						val instr = instrs match
							case Nil => Validated.error(s"No instruments for deployment $subj")
							case one :: Nil => Validated.ok(one)
							case many => Validated.error(s"Too many instruments for deployment $subj")
						instr.flatMap(getInstrumentDeployment(subj, _))
				.toIndexedSeq
			)

			val columns = columnNames.flatMap(parseJsonStringArray)
				.map{
					_.flatMap(vtLookup.lookup).toIndexedSeq
				}.orElse{ //if no actualColumnNames info is available, then all the plain mandatory columns have to be there
					Some(vtLookup.plainMandatory)
				}.filter(_.nonEmpty)

			val columnsWithDeployments = deploymentsV.map(deployments =>
				columns.map{
					_.map{vm =>
						val deps: Seq[InstrumentDeployment] = deployments.filter{dep =>
							dep.variableName.contains(vm.label) &&                //variable name matches
							dep.forProperty.exists(_.uri === vm.model.uri) &&        //variable metadata URI matches
							acq.interval.fold(false){ti =>
								dep.start.fold(true)(start => start.isBefore(ti.stop)) && //starts before data collection end
								dep.stop.fold(true)(stop => stop.isAfter(ti.start))       //ends after data collection start
							}
						}
						vm.copy(instrumentDeployments = Some(deps).filter(_.nonEmpty))
					}
				}
			)

			columnsWithDeployments.map(StationTimeSeriesMeta(acq, prod, nRows, coverage, _))
		stationTimeSeriesMeta.flatten
	end getStationTimeSerMeta

	protected def getSpatioTempMeta(dobj: IRI, vtLookup: VarMetaLookup, prodOpt: Option[DataProduction]): TSC2V[SpatioTemporalMeta] = conn ?=>

		val coverageV: Validated[GeoFeature] =
			for
				covIri <- getSingleUri(dobj, metaVocab.hasSpatialCoverage)
				cov0 <- getCoverage(covIri)
			yield
				val isCustomCoverage: Boolean = conn.primaryContextView.hasStatement(Some(covIri), Some(RDF.TYPE), None)
				if isCustomCoverage then cov0.withOptUri(None) else cov0

		val prodV = new Validated(prodOpt)

		for
			title <- getSingleString(dobj, metaVocab.dcterms.title)
			description <- getOptionalString(dobj, metaVocab.dcterms.description)
			coverage <- coverageV
			temporal <- getTemporalCoverage(dobj)
			acqOpt <- getOptionalUri(dobj, metaVocab.wasAcquiredBy)
			stationOpt <- Validated.sinkOption(acqOpt.map(getOptionalUri(_, metaVocab.prov.wasAssociatedWith)))
			station <- Validated.sinkOption(stationOpt.flatten.map(getStation))
			samplingHeightOpt <- Validated.sinkOption(acqOpt.map(getOptionalFloat(_, metaVocab.hasSamplingHeight)))
			prod <- prodV.require("Production info must be provided for a spatial data object")
			variables <- Validated.sequence(getUriValues(dobj, metaVocab.hasActualVariable).map(getL3VarInfo(_, vtLookup)))
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

	protected def getDataProduction(obj: IRI, prod: IRI): TSC2V[DataProduction] =
		for
			creatorUri <- getSingleUri(prod, metaVocab.wasPerformedBy)
			creator <- getAgent(creatorUri)
			contributors <- Validated.sequence(getUriValues(prod, metaVocab.wasParticipatedInBy).map(getAgent))
			hostUri <- getOptionalUri(prod, metaVocab.wasHostedBy)
			host <- Validated.sinkOption(hostUri.map(getOrganization))
			comment <- getOptionalString(prod, RDFS.COMMENT)
			sources <- Validated.sequence(getUriValues(obj, metaVocab.prov.hadPrimarySource).map(getPlainStaticObject))
			documentationUri <- getOptionalUri(prod, RDFS.SEEALSO)
			documentation <- Validated.sinkOption(documentationUri.map(getPlainStaticObject))
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

	private def getFundings(stat: IRI): TSC2V[Seq[Funding]] =
		Validated.sequence(getUriValues(stat, metaVocab.hasFunding).map: furi =>
			for
				self <- getLabeledResource(furi)
				funderUri <- getSingleUri(furi, metaVocab.hasFunder)
				funder <- getFunder(funderUri)
				awardTitle <- getOptionalString(furi, metaVocab.awardTitle)
				awardNumber <- getOptionalString(furi, metaVocab.awardNumber)
				awardUrl <- getOptionalUriLiteral(furi, metaVocab.awardURI)
				start <- getOptionalLocalDate(furi, metaVocab.hasStartDate)
				stop <- getOptionalLocalDate(furi, metaVocab.hasEndDate)
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
		)

	protected def getFunder(iri: IRI): TSC2V[Funder] =
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
