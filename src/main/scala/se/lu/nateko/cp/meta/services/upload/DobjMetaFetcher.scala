package se.lu.nateko.cp.meta.services.upload

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.parseJsonStringArray
import se.lu.nateko.cp.meta.utils.rdf4j._
import scala.util.Try

trait DobjMetaFetcher extends CpmetaFetcher{

	def plainObjFetcher: PlainStaticObjectFetcher

	def getSpecification(spec: IRI) = DataObjectSpec(
		self = getLabeledResource(spec),
		project = getProject(getSingleUri(spec, metaVocab.hasAssociatedProject)),
		theme = getDataTheme(getSingleUri(spec, metaVocab.hasDataTheme)),
		format = getLabeledResource(spec, metaVocab.hasFormat),
		encoding = getLabeledResource(spec, metaVocab.hasEncoding),
		dataLevel = getSingleInt(spec, metaVocab.hasDataLevel),
		datasetSpec = getOptionalUri(spec, metaVocab.containsDataset).map(getLabeledResource),
		documentation = getDocumentationObjs(spec),
		keywords = getOptionalString(spec, metaVocab.hasKeywords).map(s => parseCommaSepList(s).toIndexedSeq)
	)

	private def getDocumentationObjs(item: IRI): Seq[PlainStaticObject] =
		server.getUriValues(item, metaVocab.hasDocumentationObject).map(plainObjFetcher.getPlainStaticObject)

	def getOptionalStation(station: IRI): Option[Station] = Try(getStation(station)).toOption

	protected  def getStation(stat: IRI) = Station(
		org = getOrganization(stat),
		id = getOptionalString(stat, metaVocab.hasStationId).getOrElse("Unknown"),
		name = getOptionalString(stat, metaVocab.hasName).getOrElse("Unknown"),
		coverage = getStationCoverage(stat),
		responsibleOrganization = getOptionalUri(stat, metaVocab.hasResponsibleOrganization).map(getOrganization),
		sites = Option(server.getUriValues(stat, metaVocab.operatesOn).map(getSite)).filter(_.nonEmpty),
		ecosystems = Option(server.getUriValues(stat, metaVocab.hasEcosystemType).map(getLabeledResource)).filter(_.nonEmpty),
		climateZone = getOptionalUri(stat, metaVocab.hasClimateZone).map(getLabeledResource),
		meanAnnualTemp = getOptionalFloat(stat, metaVocab.hasMeanAnnualTemp),
		operationalPeriod = getOptionalString(stat, metaVocab.hasOperationalPeriod),
		website = getOptionalUriLiteral(stat, RDFS.SEEALSO),
		documentation = getDocumentationObjs(stat),
		pictures = Option(server.getUriLiteralValues(stat, metaVocab.hasDepiction)).filter(_.nonEmpty)
	)

	protected def getL2Meta(dobj: IRI, vtLookup: ValueTypeLookup[IRI], prod: Option[DataProduction]): L2OrLessSpecificMeta = {
		val acqUri = getSingleUri(dobj, metaVocab.wasAcquiredBy)

		val acq = DataAcquisition(
			station = getStation(getSingleUri(acqUri, metaVocab.prov.wasAssociatedWith)),
			site = getOptionalUri(acqUri, metaVocab.wasPerformedAt).map(getSite),
			interval = for(
				start <- getOptionalInstant(acqUri, metaVocab.prov.startedAtTime);
				stop <- getOptionalInstant(acqUri, metaVocab.prov.endedAtTime)
			) yield TimeInterval(start, stop),
			instrument = server.getUriValues(acqUri, metaVocab.wasPerformedWith).map(_.toJava).toList match{
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

}
