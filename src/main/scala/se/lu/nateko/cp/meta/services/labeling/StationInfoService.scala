package se.lu.nateko.cp.meta.services.labeling

import org.eclipse.rdf4j.model.{IRI, Literal, Statement}
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.onto.InstOnto
import se.lu.nateko.cp.meta.utils.rdf4j.*
import spray.json.{JsObject, JsString}

import java.net.URI
import java.time.Instant

trait StationInfoService:
	self: StationLabelingService =>

	import StatementSource.*
	import LabelingDb.{ProvConn, IcosConn}

	private lazy val dataTypeInfos = {
		import org.semanticweb.owlapi.model.IRI
		import se.lu.nateko.cp.meta.ClassDto
		import se.lu.nateko.cp.meta.DataPropertyDto

		def toDatatypeLookup(classInfo: ClassDto) =
			classInfo.properties.collect{
				case DataPropertyDto(prop, _, range) => (prop.uri, range.dataType)
			}.toMap

		val stationClass = onto.factory.getOWLClass(IRI.create(vocab.station.toJava))
		onto.getBottomSubClasses(stationClass)
			.map(onto.getClassInfo)
			.map(classInfo => (classInfo.resource.uri, toDatatypeLookup(classInfo)))
			.toMap
	}

	def saveStationInfo(info: JsObject, uploader: UserId): Unit =

		val stationUri = info.fields.get("stationUri")
			.collect{case JsString(str) => str}
			.map(factory.createIRI).get

		val (currentInfo, newInfo) = db.accessLbl:
			val provConn = db.provView
			assertThatWriteIsAuthorized(stationUri, uploader)(using provConn)

			val newInfo: Seq[Statement] =
				for
					classUri <- lookupStationClass(stationUri)(using provConn).toSeq
					(fieldName, fieldValue) <- info.fields.collect:
						case (name, JsString(value)) => (name, value)
					propUri = vocab.getProperty(fieldName)
					dataType <- lookupDatatype(classUri.toJava, propUri.toJava).toSeq
				yield
					val lit = factory.createLiteral(fieldValue, dataType)
					factory.createStatement(stationUri, propUri, lit)

			getStatements(stationUri) -> newInfo

		db.applyLblDiff(currentInfo.filter(notProtected), newInfo.filter(notProtected))

	end saveStationInfo


	def labelingHistory: Seq[StationLabelingHistory] =
		import vocab.{hasShortName, hasApplicationStatus}

		val histLookup = (db.provRdfLog.timedUpdates ++ db.labelingRdfLog.timedUpdates)
			.foldLeft(Map.empty[IRI, LabelingHistory]){case (map, (ts, upd)) =>
				if(!upd.isAssertion) map else upd.statement match{
					case Rdf4jStatement(stationIri, `hasShortName`, _) =>
						map.updatedWith(stationIri)(_.orElse(Some(StationLabelingHistory.empty(ts))))

					case Rdf4jStatement(stationIri, `hasApplicationStatus`, statusLit : Literal) =>
						map.updatedWith(stationIri)(_.map(updateHistory(_, statusLit, ts)))

					case _ => map
				}
			}
		val histIter = db.accessIcos: icosConn ?=>
			db.accessProv:
				for
					(iri, hist) <- histLookup
					stInfo <- getStationBasicInfo(iri, icosConn)
				yield
					new StationLabelingHistory(stInfo, hist)
		histIter.toSeq.sorted(using StationLabelingHistory.histOrder)


	private def updateHistory(hist: LabelingHistory, statusLit: Literal, ts: Instant): LabelingHistory =
		val statusStr = statusLit.stringValue
		import LifecycleService.AppStatus
		import AppStatus.*

		def statusIs(status: AppStatus): Boolean = status.toString == statusStr
		import LabelingProgressDates.empty
		import hist.progress
		val progressUpdate: LabelingProgressDates =
			if statusIs(step1submitted) then
				if progress.step1start.isDefined then empty else empty.copy(step1start = Some(ts))
			else if statusIs(step1approved) then
				empty.copy(step1approval = Some(ts))
			else if statusIs(step2ontrack) || statusIs(step2started_old) then
				if progress.step2start.isDefined then empty else empty.copy(step2start = Some(ts))
			else if statusIs(step2approved) then
				empty.copy(step2approval = Some(ts))
			else if statusIs(step3approved) then
				empty.copy(labelled = Some(ts))
			else empty
		hist.withOverrides(progressUpdate)


	private def lookupDatatype(classUri: java.net.URI, propUri: java.net.URI): Option[IRI] =
		dataTypeInfos.get(classUri).flatMap(_.get(propUri)).map(uri => factory.createIRI(uri))

	private def notProtected(statement: Statement): Boolean = statement match{
		case Rdf4jStatement(_, pred, _) if protectedPredicates.contains(pred) => false
		case _ => true
	}

	private def getStationBasicInfo(provUri: IRI, icosConn: IcosConn)(using ProvConn): Option[StationBasicInfo] =
		val provIdOpt = getStringValues(provUri, vocab.hasShortName).headOption
		val provNameOpt = getStringValues(provUri, vocab.hasLongName).headOption
		val themeOpt = InstOnto.getSingleTypeIfAny(provUri).collect:
			case t if t === vocab.atmoStationClass => "Atmosphere"
			case t if t === vocab.ecoStationClass => "Ecosystem"
			case t if t === vocab.oceStationClass => "Ocean"

		val provClassOpt = getStringValues(provUri, vocab.hasStationClass).headOption
		val prodUriOpt = getUriLiteralValues(provUri, vocab.hasProductionCounterpart).headOption.map(_.toRdf)

		def prodStr(pred: IRI): Option[String] =
			prodUriOpt.flatMap(prodUri => getSingleString(prodUri, pred)(using icosConn).result)

		def progrDate(pred: IRI): Option[Instant] = getOptionalInstant(provUri, pred).result.get

		val labelingProgress = LabelingProgressDates(
			step1start = progrDate(vocab.step1StartDate),
			step1approval = progrDate(vocab.step1EndDate),
			step2start = progrDate(vocab.step2StartDate),
			step2approval = progrDate(vocab.step2EndDate),
			labelled = progrDate(vocab.labelingEndDate)
		)

		for(provId <- provIdOpt; provName <- provNameOpt; theme <- themeOpt; provClass <- provClassOpt) yield
			StationBasicInfo(
				provId = provId,
				prodId = prodStr(metaVocab.hasStationId),
				provName = provName,
				prodName = prodStr(metaVocab.hasName),
				provUri = provUri.toJava,
				prodUri = prodUriOpt.map(_.toJava),
				theme = theme,
				provClass = provClass,
				prodClass = prodStr(metaVocab.hasStationClass),
				joinYear = getIntValues(provUri, vocab.labelingJoinYear).headOption,
				labelingProgressOverrides = labelingProgress
			)
	end getStationBasicInfo
end StationInfoService

case class LabelingProgressDates(
	step1start: Option[Instant],
	step1approval: Option[Instant],
	step2start: Option[Instant],
	step2approval: Option[Instant],
	labelled: Option[Instant]
)
object LabelingProgressDates:
	def empty = LabelingProgressDates(None, None, None, None, None)

case class LabelingHistory(added: Instant, progress: LabelingProgressDates):
	def withOverrides(overrides: LabelingProgressDates): LabelingHistory = copy(
		progress = progress.copy(
			step1start = overrides.step1start.orElse(progress.step1start),
			step1approval = overrides.step1approval.orElse(progress.step1approval),
			step2start = overrides.step2start.orElse(progress.step2start),
			step2approval = overrides.step2approval.orElse(progress.step2approval),
			labelled = overrides.labelled.orElse(progress.labelled)
		)
	)

case class StationBasicInfo(
	provId: String,
	prodId: Option[String],
	provName: String,
	prodName: Option[String],
	provUri: URI,
	prodUri: Option[URI],
	theme: String,
	provClass: String,
	prodClass: Option[String],
	joinYear: Option[Int],
	labelingProgressOverrides: LabelingProgressDates
):
	def bestName = prodName.getOrElse(provName)

class StationLabelingHistory(val station: StationBasicInfo, val hist: LabelingHistory)

object StationLabelingHistory{

	def empty(ts: Instant) = LabelingHistory(ts, LabelingProgressDates.empty)

	val CsvHeader = "Theme,ID,Name,Class,JoinYear,AddedToDb,Step1Start,Step1End,Step2Start,Step2End,Labelled"

	def toCsvRow(shist: StationLabelingHistory): String = {
		val hist = shist.hist.withOverrides(shist.station.labelingProgressOverrides)
		import hist.progress.*
		val st = shist.station
		val cells: Seq[String] =
			Seq(st.theme, st.prodId.getOrElse(st.provId), st.bestName, st.prodClass.getOrElse(st.provClass)) ++
			Seq(st.joinYear.fold("")(_.toString)) ++
			Seq(Some(hist.added), step1start, step1approval, step2start, step2approval, labelled).map(cell)
		cells.mkString("\n", ",", "")
	}

	private def cell(ts: Option[Instant]): String = ts.fold("")(_.toString)

	val histOrder = Ordering.by[StationLabelingHistory, String](_.station.theme).orElseBy(_.station.bestName)
}
