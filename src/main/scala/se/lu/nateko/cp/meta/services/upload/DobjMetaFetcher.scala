package se.lu.nateko.cp.meta.services.upload

import java.time.LocalDate
import java.time.ZoneId

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDFS

import scala.util.Try

import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.parseJsonStringArray
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.instanceserver.InstanceServerUtils
import se.lu.nateko.cp.meta.services.MetadataException
import org.eclipse.rdf4j.model.vocabulary.RDF
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

		val deployments = server.getStatements(None, Some(metaVocab.atOrganization), Some(stationUri)).collect{
			case Rdf4jStatement(subj, _, _) if server.hasStatement(subj, RDF.TYPE, metaVocab.ssn.deploymentClass) =>
				val instr = server.getStatements(None, Some(metaVocab.ssn.hasDeployment), Some(subj)).collect{
					case Rdf4jStatement(instr, _, _) => instr
				}.toList match
					case Nil => throw new Exception(s"No instruments for deployment $subj")
					case one :: Nil => one
					case many => throw new Exception(s"Too many instruments for deployment $subj")
				getInstrumentDeployment(subj, instr)
		}.toIndexedSeq

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

	def getPlainStaticObject(dobj: IRI): TSC2V[PlainStaticObject] =
		for
			hashsum <- getHashsum(dobj, metaVocab.hasSha256sum)
			fileName <- getOptionalString(dobj, metaVocab.dcterms.title).flatMap:
				case None => getSingleString(dobj, metaVocab.hasName)
				case Some(title) => Validated.ok(title)
		yield
			PlainStaticObject(dobj.toJava, hashsum, fileName)

	def getDocument(dobj: IRI): TSC2V[PlainStaticObject] = conn ?=>
		getPlainStaticObject(dobj)(using conn.withReadContexts(Seq(documentsGraph)))

end DobjMetaReader
