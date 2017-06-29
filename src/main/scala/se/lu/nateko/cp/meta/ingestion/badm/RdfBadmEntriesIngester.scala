package se.lu.nateko.cp.meta.ingestion.badm

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.XMLSchema

import BadmConsts._
import BadmSchema.PropertyInfo
import BadmSchema.Schema
import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.ingestion.Ingester
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.sesame.EnrichedValueFactory

class RdfBadmEntriesIngester(entries: => Iterable[BadmEntry], schema: => Schema) extends Ingester{

	import RdfBadmEntriesIngester._

	private case class ScanAcc(
		siteId: Option[String],
		varValueCounts: Map[String, Int],
		statements: Seq[Statement]
	)

	private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

	def getStatements(f: ValueFactory): Iterator[Statement] = {
		implicit val vocab = new CpVocab(f)
		implicit val metaVocab = new CpmetaVocab(f)
		implicit val badmVocab = new BadmVocab(f)

		entries.iterator.scanLeft[ScanAcc](ScanAcc(None, Map.empty, Seq.empty)){
			(acc, entry) => entry.variable match {

				case SiteVar =>
					ScanAcc(getSiteId(entry), Map.empty, Seq.empty)

				case LocationVar | InstrumentVar =>
					acc.copy(statements = Seq.empty)

				case TeamMemberVar =>
					val siteId = acc.siteId.get //will throw here if no site id
					acc.copy(statements = getTeamMemberStatements(siteId, entry))

				case variable =>
					val siteId = acc.siteId.get //will throw here if no site id
					val station = vocab.getEcosystemStation(siteId)
					val varCount = acc.varValueCounts.getOrElse(variable, -1) + 1
					val ancillEntry = {
						val ancillEntryId = s"${siteId}_${variable}_${varCount}"
						vocab.getAncillaryEntry(ancillEntryId)
					}
					acc.copy(
						varValueCounts = acc.varValueCounts + (variable -> varCount),
						statements = getEntryStatements(station, entry, ancillEntry)
					)
			}
		}.flatMap(_.statements)
	}

	private def getTeamMemberStatements(siteId: String, entry: BadmEntry)
				(implicit vocab: CpVocab, metaVocab: CpmetaVocab): Seq[Statement] = {

		val varToVal: Map[String, String] = entry.values.map(bv => (bv.variable, bv.valueStr)).toMap

		//logger.info("Parsing name: " + varToVal(TeamMemberNameVar))
		val (firstName, lastName) = varToVal(TeamMemberNameVar) match {
			case standardNameRegex(firstName, lastName) => (firstName, lastName)
			case withMiddleNameRegex(firstName, lastName) => (firstName, lastName)
		}

		val email = varToVal(TeamMemberEmailVar).toLowerCase
		val roleId = varToVal(TeamMemberRoleVar)
		val membership = vocab.getEtcMembership(siteId, roleId, lastName)
		val person = vocab.getPerson(firstName, lastName)

		val membershipStart = entry.date.map(_ match {
			case BadmYear(year) => vocab.lit(year.toString, XMLSchema.DATE)
			case BadmLocalDate(date) => vocab.lit(date)
		}).getOrElse(vocab.lit(entry.submissionDate))

		Seq[(IRI, IRI, Value)](
			(person, RDF.TYPE, metaVocab.personClass),
			(person, metaVocab.hasFirstName, vocab.lit(firstName)),
			(person, metaVocab.hasLastName, vocab.lit(lastName)),
			(person, metaVocab.hasEmail, vocab.lit(email)),
			(person, metaVocab.hasMembership, membership),
			(membership, metaVocab.atOrganization, vocab.getEcosystemStation(siteId)),
			(membership, metaVocab.hasRole, vocab.getRole(roleId)),
			(membership, metaVocab.hasStartTime, membershipStart)
		) map metaVocab.factory.tripleToStatement
	}

	private def getEntryStatements(station: IRI, entry: BadmEntry, ancillEntry: IRI)
				(implicit metaVocab: CpmetaVocab, badmVocab: BadmVocab): Seq[Statement] = {
		val submissionDate = metaVocab.lit(entry.submissionDate)

		val entryInformationDate: Option[Value] = entry.date.map{
			case BadmLocalDate(locDate) => metaVocab.lit(locDate)
			case BadmYear(year) => metaVocab.lit(year.toString, XMLSchema.DATE)
		}

		Seq[(IRI, IRI, Value)](
			(station, metaVocab.hasAncillaryEntry, ancillEntry),
			(ancillEntry, RDF.TYPE, metaVocab.ancillaryEntryClass),
			(ancillEntry, metaVocab.dcterms.dateSubmitted, submissionDate)
		) ++
		entryInformationDate.map{
			(ancillEntry, metaVocab.dcterms.date, _)
		} ++
		entry.values.map(badmValue2PropValue).collect{
			case Some((prop, value)) => (ancillEntry, prop, value)
		} map metaVocab.factory.tripleToStatement
	}

	private def badmValue2PropValue(badmValue: BadmValue)(implicit badmVocab: BadmVocab): Option[(IRI, Value)] = {
		val variable = badmValue.variable
		val valueString = badmValue.valueStr

		schema.get(variable) match{

			case Some(PropertyInfo(_, _, None)) =>
				val prop = badmVocab.getDataProp(variable)
				Some((prop, getPlainValue(badmValue)))

			case Some(PropertyInfo(_, _, Some(badmVarVocab))) =>
				if(badmVarVocab.contains(valueString)) {
					val prop = badmVocab.getObjProp(variable)
					val badmValue = badmVocab.getVocabValue(variable, valueString)
					Some((prop, badmValue))
				} else {
					logger.error(s"Value '$valueString' is not in BADM vocabulary for variable $variable")
					None
				}
			case None => {
				logger.error(s"Variable '$variable' is not present in BADM schema")
				None
			}
		}
	}
}

object RdfBadmEntriesIngester{

	def getSiteId(siteEntry: BadmEntry): Option[String] =
		siteEntry.values.collectFirst{
			case BadmStringValue(SiteIdVar, siteId) => siteId
		}

	def getPlainValue(badmValue: BadmValue)(implicit vocab: CustomVocab): Value = badmValue match {
		case BadmStringValue(_, value) => vocab.lit(value)
		case BadmNumericValue(_, value, _) => vocab.lit(value, XMLSchema.DOUBLE)
	}

}
