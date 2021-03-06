package se.lu.nateko.cp.meta.services.upload

import java.time.LocalDate
import java.time.ZoneId

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XMLSchema

import scala.util.Try

import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.parseJsonStringArray
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper

trait DobjMetaFetcher extends CpmetaFetcher{

	def plainObjFetcher: PlainStaticObjectFetcher
	protected def vocab: CpVocab

	def getSpecification(spec: IRI) = DataObjectSpec(
		self = getLabeledResource(spec),
		project = getProject(getSingleUri(spec, metaVocab.hasAssociatedProject)),
		theme = getDataTheme(getSingleUri(spec, metaVocab.hasDataTheme)),
		format = getLabeledResource(spec, metaVocab.hasFormat),
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
			coverage = getStationCoverage(stat, Some(s"${org.name} geo-coverage")),
			responsibleOrganization = getOptionalUri(stat, metaVocab.hasResponsibleOrganization).map(getOrganization),
			specificInfo = getStationSpecifics(stat),
			pictures = server.getUriLiteralValues(stat, metaVocab.hasDepiction),
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
				documentation = getDocumentationObjs(stat)
			)
		else if(server.resourceHasType(stat, metaVocab.ecoStationClass)){
			val icosSpecif = getIcosStationSpecifics(stat, vocab.etc)
			EtcStationSpecifics(
				theme = icosSpecif.theme,
				stationClass = icosSpecif.stationClass,
				labelingDate = icosSpecif.labelingDate,
				discontinued = icosSpecif.discontinued,
				countryCode = icosSpecif.countryCode,
				climateZone = getOptionalUri(stat, metaVocab.hasClimateZone).map(getLabeledResource),
				ecosystemType = getOptionalUri(stat, metaVocab.hasEcosystemType).map(getLabeledResource),
				meanAnnualTemp = getOptionalFloat(stat, metaVocab.hasMeanAnnualTemp),
				meanAnnualPrecip = getOptionalFloat(stat, metaVocab.hasMeanAnnualPrecip),
				meanAnnualRad = getOptionalFloat(stat, metaVocab.hasMeanAnnualRadiation),
				stationDocs = server.getUriLiteralValues(stat, metaVocab.hasDocumentationUri),
				stationPubs = server.getUriLiteralValues(stat, metaVocab.hasAssociatedPublication),
				timeZoneOffset = getOptionalInt(stat, metaVocab.hasTimeZoneOffset),
				documentation = getDocumentationObjs(stat)
			)
		} else if(server.resourceHasType(stat, metaVocab.atmoStationClass))
			getIcosStationSpecifics(stat, vocab.atc)
		else if(server.resourceHasType(stat, metaVocab.oceStationClass))
			getIcosStationSpecifics(stat, vocab.otc)
		else NoStationSpecifics
	}

	private def getIcosStationSpecifics(stat: IRI, thematicCenter: IRI) = {
		val (lblDate, discont) = getLabelingDateAndDiscontinuation(stat)
		PlainIcosSpecifics(
			theme = getOptionalUri(thematicCenter, metaVocab.hasDataTheme).map(getDataTheme),
			stationClass = getOptionalString(stat, metaVocab.hasStationClass).map(IcosStationClass.withName),
			labelingDate = lblDate,
			discontinued = discont,
			countryCode = getOptionalString(stat, metaVocab.countryCode).flatMap(CountryCode.unapply),
			timeZoneOffset = getOptionalInt(stat, metaVocab.hasTimeZoneOffset),
			documentation = getDocumentationObjs(stat)
		)
	}

	private def getLabelingDateAndDiscontinuation(stat: IRI): (Option[LocalDate], Boolean) = {
		//one-off local hack to avoid extensive config for fetching the labeling date from the labeling app metadata layer

		val ctxts = Seq(
			"http://meta.icos-cp.eu/resources/stationentry/",
			"http://meta.icos-cp.eu/resources/stationlabeling/"
		).map(server.factory.createIRI)

		val fetcher = FetchingHelper(server.withContexts(ctxts, Nil))

		val Seq(prodStLink, appStatus, statusDate, stationId) = Seq(
				"hasProductionCounterpart", "hasApplicationStatus", "hasAppStatusDate", "hasShortName"
			)
			.map(server.factory.createIRI("http://meta.icos-cp.eu/ontologies/stationentry/", _))

		val provStOpt: Option[IRI] = fetcher.server
			.getStatements(None, Some(prodStLink), Some(vocab.lit(stat.toJava)))
			.collect{
				case Rdf4jStatement(provSt, _, _) if fetcher.server.hasStatement(provSt, appStatus, vocab.lit("STEP3APPROVED")) => provSt
			}
			.toIndexedSeq.headOption

		val labelingDate = provStOpt.flatMap{labeledSt =>
			fetcher
				.getOptionalInstant(labeledSt, statusDate)
				.map(_.atZone(ZoneId.of("UTC")).toLocalDate)
		}

		val discontinued: Boolean = provStOpt.fold(true){provSt =>
			!fetcher.server.hasStatement(Some(provSt), Some(stationId), None)
		}

		labelingDate -> discontinued
	}

	protected def getL2Meta(dobj: IRI, vtLookup: ValueTypeLookup[IRI], prod: Option[DataProduction]): L2OrLessSpecificMeta = {
		val acqUri = getSingleUri(dobj, metaVocab.wasAcquiredBy)

		val acq = DataAcquisition(
			station = getStation(getSingleUri(acqUri, metaVocab.prov.wasAssociatedWith)),
			site = getOptionalUri(acqUri, metaVocab.wasPerformedAt).map(getSite),
			interval = for(
				start <- getOptionalInstant(acqUri, metaVocab.prov.startedAtTime);
				stop <- getOptionalInstant(acqUri, metaVocab.prov.endedAtTime)
			) yield TimeInterval(start, stop),
			instrument = server.getUriValues(acqUri, metaVocab.wasPerformedWith).map(getInstrumentLite).toList match{
				case Nil => None
				case single :: Nil => Some(Left(single))
				case many => Some(Right(many))
			},
			samplingPoint = getOptionalUri(acqUri, metaVocab.hasSamplingPoint).map(getPosition),
			samplingHeight = getOptionalFloat(acqUri, metaVocab.hasSamplingHeight)
		)
		val nRows = getOptionalInt(dobj, metaVocab.hasNumberOfRows)

		val coverage = getOptionalUri(dobj, metaVocab.hasSpatialCoverage).map(getCoverage)

		val columns = getOptionalString(dobj, metaVocab.hasActualColumnNames).flatMap(parseJsonStringArray)
			.map{
				_.flatMap{colName =>
					vtLookup.lookup(colName).map{vtUri =>
						val valType = getValueType(vtUri)
						ColumnInfo(colName, valType)
					}
				}.toIndexedSeq
			}.orElse{ //if no actualColumnNames info is available, then all the mandatory columns have to be there
				Some(
					vtLookup.plainMandatory.map{
						case (colName, valTypeIri) => ColumnInfo(colName, getValueType(valTypeIri))
					}
				)
			}.filter(_.nonEmpty)

		L2OrLessSpecificMeta(acq, prod, nRows, coverage, columns)
	}


	protected def getDataProduction(obj: IRI, prod: IRI) = DataProduction(
		creator = getAgent(getSingleUri(prod, metaVocab.wasPerformedBy)),
		contributors = server.getUriValues(prod, metaVocab.wasParticipatedInBy).map(getAgent),
		host = getOptionalUri(prod, metaVocab.wasHostedBy).map(getOrganization),
		comment = getOptionalString(prod, RDFS.COMMENT),
		sources = server.getUriValues(obj, metaVocab.prov.hadPrimarySource).map(plainObjFetcher.getPlainStaticObject).map(_.asUriResource),
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
			idType <- Try(FunderIdType.withName(idTypeStr)).toOption
		) yield idStr -> idType
	)

}
