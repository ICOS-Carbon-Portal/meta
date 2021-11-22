package se.lu.nateko.cp.meta.services.labeling

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.IRI

import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.utils.rdf4j._
import spray.json.JsObject
import spray.json.JsString
import java.time.Instant
import org.eclipse.rdf4j.model.Literal

trait StationInfoService { self: StationLabelingService =>

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

	def saveStationInfo(info: JsObject, uploader: UserId): Unit = {

		val stationUri = info.fields.get("stationUri")
			.collect{case JsString(str) => str}
			.map(factory.createIRI).get

		assertThatWriteIsAuthorized(stationUri, uploader)

		val newInfo: Seq[Statement] = for(
			classUri <- lookupStationClass(stationUri).toSeq;
			(fieldName, fieldValue) <- info.fields.collect{case (name, JsString(value)) => (name, value)};
			propUri = vocab.getProperty(fieldName);
			dataType <- lookupDatatype(classUri.toJava, propUri.toJava).toSeq
		) yield {
			val lit = factory.createLiteral(fieldValue, dataType)
			factory.createStatement(stationUri, propUri, lit)
		}

		val currentInfo = server.getStatements(stationUri)

		server.applyDiff(currentInfo.filter(notProtected), newInfo.filter(notProtected))
	}

	def labelingHistory: Iterable[StationLabelingHistory] = {
		import vocab.{hasShortName, hasApplicationStatus}

		val histLookup = (provRdfLog.timedUpdates ++ labelingRdfLog.timedUpdates)
			.foldLeft(Map.empty[IRI, LabelingHistory]){case (map, (ts, upd)) =>
				if(!upd.isAssertion) map else upd.statement match{
					case Rdf4jStatement(stationIri, `hasShortName`, _) =>
						map.updatedWith(stationIri)(_.orElse(Some(StationLabelingHistory.empty(ts))))

					case Rdf4jStatement(stationIri, `hasApplicationStatus`, statusLit : Literal) =>
						map.updatedWith(stationIri)(_.map(updateHistory(_, statusLit, ts)))

					case _ => map
				}
			}
		for((iri, hist) <- histLookup; stId <- lookupStationId(iri))
			yield new StationLabelingHistory(stId, iri, hist)
	}

	private def updateHistory(hist: LabelingHistory, statusLit: Literal, ts: Instant): LabelingHistory = {
		val statusStr = statusLit.stringValue
		import LifecycleService.AppStatus._

		def statusIs(status: AppStatus): Boolean = status.toString == statusStr

		if(statusIs(step1submitted)) {
			if(hist.step1start.isDefined) hist else hist.copy(step1start = Some(ts))
		} else if(statusIs(step1approved))
			hist.copy(step1approval = Some(ts))
		else if(statusIs(step2ontrack) || statusIs(step2started_old)){
			if(hist.step2start.isDefined) hist else hist.copy(step2start = Some(ts))
		} else if(statusIs(step2approved))
			hist.copy(step2approval = Some(ts))
		else if(statusIs(step3approved))
			hist.copy(labelled = Some(ts))
		else hist
	}

	private def lookupDatatype(classUri: java.net.URI, propUri: java.net.URI): Option[IRI] =
		dataTypeInfos.get(classUri).flatMap(_.get(propUri)).map(uri => factory.createIRI(uri))

	private def notProtected(statement: Statement): Boolean = statement match{
		case Rdf4jStatement(_, pred, _) if protectedPredicates.contains(pred) => false
		case _ => true
	}
}

case class LabelingHistory(
	added: Instant,
	step1start: Option[Instant],
	step1approval: Option[Instant],
	step2start: Option[Instant],
	step2approval: Option[Instant],
	labelled: Option[Instant]
)

class StationLabelingHistory(val provId: String, val iri: IRI, val hist: LabelingHistory)

object StationLabelingHistory{

	def empty(ts: Instant) = LabelingHistory(ts, None, None, None, None, None)

	val CsvHeader = "ProvisID,ProvisURI,Added,Step1Start,Step1End,Step2Start,Step2End,Labelled"

	def toCsvRow(shist: StationLabelingHistory): String = {
		val hist = shist.hist
		val cells = Seq(shist.provId, shist.iri.stringValue) ++ Seq(
			Some(hist.added), hist.step1start, hist.step1approval, hist.step2start, hist.step2approval, hist.labelled
		).map(cell)
		cells.mkString("\n", ",", "")
	}

	private def cell(ts: Option[Instant]): String = ts.fold("")(_.toString)
}
