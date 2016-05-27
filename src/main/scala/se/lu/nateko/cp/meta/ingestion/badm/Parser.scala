package se.lu.nateko.cp.meta.ingestion.badm

import java.text.NumberFormat
import java.text.ParseException
import java.time.LocalDate
import java.util.Locale

import scala.annotation.migration
import scala.io.Source

import BadmConsts._
import com.opencsv.CSVReader
import java.io.InputStreamReader
import spray.json.JsArray
import spray.json.JsNumber
import spray.json.JsObject
import spray.json.JsString

object Parser {

	def parseEntriesFromCsv(src: java.io.InputStream): Seq[BadmEntry] = {
		aggregateEntries(getCsvRows(src).map(csvRowToRawEntry).toSeq)
	}

	def parseEntriesFromEtcJson(entries: JsObject): Seq[BadmEntry] = {
		entries.fields.get("d").toSeq.collect{
			case JsArray(entries) => entries
				.collect{
					case obj: JsObject => etcJsonRowToSiteIdAndRawEntry(obj).toSeq
				}.flatten
				.groupBy(_._1) //by site id
				.mapValues{
					idAndRaw => aggregateEntries(idAndRaw.map(_._2).sortBy(_.id))
				}
				.values
		}.flatten.flatten
	}

	def getCsvRows(csvStream: java.io.InputStream): Stream[Array[String]] = {
		val reader = new InputStreamReader(csvStream)
		val csvReader = new CSVReader(reader, ',', '"')
		val iter = csvReader.iterator()

		def getStream: Stream[Array[String]] = {
			if(iter.hasNext) iter.next() #:: getStream
			else {
				csvReader.close()
				Stream.empty
			}
		}
		getStream.drop(1)
	}

	private def aggregateEntries(raw: Seq[BadmRawEntry]): Seq[BadmEntry] = {

		val parser = NumberFormat.getNumberInstance(Locale.ROOT)

		def fromSameId(raw: Seq[BadmRawEntry]): BadmEntry = {
			val values = raw.map{re =>
				try{
					BadmNumericValue(re.qualifier, re.value, parser.parse(re.value))
				}catch{
					case err: ParseException =>
						BadmStringValue(re.qualifier, re.value)
				}
			}.toIndexedSeq
			BadmEntry(raw.head.variable, values, raw.head.valueDate, raw.head.submissionDate)
		}
		raw.filter(_.qualifier != DateQualifier).groupBy(_.id).toSeq
			.sortBy(_._1).map(_._2).map(fromSameId)
	}

	private def etcJsonRowToSiteIdAndRawEntry(entry: JsObject): Option[(String, BadmRawEntry)] = {
		val fields = entry.fields
		def getStr(fieldName: String): Option[String] =
			fields.get(fieldName).collect{case JsString(s) => s}
		for(
			id <- fields.get("Index").collect{case JsNumber(n) => n.toInt};
			variable <- getStr("VarName");
			qualifier0 <- getStr("QualName");
			value0 <- getStr("Value");
			valueDate = getStr("IsoDate").flatMap(toValueDate);
			submissionDate <- getStr("SubDate").map(toIsoDate);
			siteId <- getStr("SiteName")
		) yield {
			val (qualifier, value) = getHarmonizedQualifierAndValue(variable, qualifier0, value0)
			(siteId, BadmRawEntry(id, variable, qualifier, value, valueDate, submissionDate))
		}
	}

	private def csvRowToRawEntry(cells: Array[String]): BadmRawEntry = {
		val variable = cells(1)
		val (qualifier, value) = getHarmonizedQualifierAndValue(variable, cells(2), cells(3))

		BadmRawEntry(
			id = cells(0).toInt,
			variable = variable,
			qualifier = qualifier,
			value = value,
			valueDate =  toValueDate(cells(4)),
			submissionDate = toIsoDate(cells(5))
		)
	}

	private def getHarmonizedQualifierAndValue(
		variable: String,
		qualifier: String,
		value: String
	): (String, String) = qualifier match {
		case ValueQualifier => (variable, value)
		case VariableVar => value match {
			case varCodeRegex(varCode) => (VarCodeVar, varCode)
		}
		case PercentQualifier => (variable + "_" + PercentQualifier, value)
		case _ => (qualifier, value)
	}

	def toBadmDate(badmDate: String): BadmDate = {
		badmDate match{
			case badmDateRegex(year, month, day) =>
				BadmLocalDate(LocalDate.parse(s"$year-$month-$day"))
			case badmDateAnyTimeRegex(year, month, day) =>
				BadmLocalDate(LocalDate.parse(s"$year-$month-$day"))
			case yearRegex(year) =>
				BadmYear(year.toInt)
			case _ =>
				throw new ParseException(s"$badmDate is not a valid BADM date string", -1)
		}
	}

	private def toIsoDate(badmDate: String): LocalDate = toBadmDate(badmDate) match {
		case BadmLocalDate(date) => date
		case _ => throw new ParseException(s"$badmDate is not convertible to ISO8601 date", -1)
	}

	private def toValueDate(valueDate: String): Option[BadmDate] =
		if(valueDate == "-9999") None
		else Some(toBadmDate(valueDate))
}
