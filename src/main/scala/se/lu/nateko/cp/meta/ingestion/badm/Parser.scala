package se.lu.nateko.cp.meta.ingestion.badm

import java.text.NumberFormat
import java.text.ParseException
import java.time.LocalDate
import java.util.Locale

import BadmConsts._
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder

import spray.json.JsArray
import spray.json.JsNumber
import spray.json.JsObject
import spray.json.JsString
import java.io.StringReader
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

import se.lu.nateko.cp.meta.core.etcupload.StationId
import spray.json.JsValue

object Parser {

	def parseEntriesFromCsv(src: String): Seq[BadmEntry] = {
		aggregateEntries(None, getCsvRows(src).map(csvRowToRawEntry).toSeq)
	}

	def parseEntriesFromEtcJson(entries: JsValue): Seq[BadmEntry] = {
		entries match{
			case JsArray(entries) => entries
				.collect{
					case obj: JsObject => etcJsonRowToSiteIdAndRawEntry(obj)
				}.flatten
				.groupBy(_._1) //by site id
				.mapValues{
					idAndRaw => aggregateEntries(
						idAndRaw.headOption.map{case (StationId(id), _) => id},
						idAndRaw.map(_._2).sortBy(_.id)
					)
				}
				.values.flatten.toSeq
			case JsObject(fields) =>
				fields.get("d").map(parseEntriesFromEtcJson).getOrElse(Nil)
			case _ => Nil
		}
	}

	def getCsvRows(csvStream: String): Stream[Array[String]] = {
		val reader = new StringReader(csvStream)
		val csvParser = new CSVParserBuilder().withSeparator(',').withQuoteChar('"').build
		val csvReader = new CSVReaderBuilder(reader).withCSVParser(csvParser).build
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

	private def aggregateEntries(station: Option[StationId], raw: Seq[BadmRawEntry]): Seq[BadmEntry] = {

		val parser = NumberFormat.getNumberInstance(Locale.ROOT)

		def fromSameId(raw: Seq[BadmRawEntry]): BadmEntry = {
			val values = raw.map{re =>
				try{
					BadmNumericValue(re.qualifier, re.value, parser.parse(re.value))
				}catch{
					case _: ParseException =>
						BadmStringValue(re.qualifier, re.value)
				}
			}.toIndexedSeq
			val firstRaw = raw.head
			import firstRaw._
			BadmEntry(variable, values, valueDate, station, submissionDate)
		}
		raw.filter(_.qualifier != DateQualifier).groupBy(_.id).toSeq
			.sortBy(_._1).map(_._2).map(fromSameId)
	}

	private def etcJsonRowToSiteIdAndRawEntry(entry: JsObject): Option[(String, BadmRawEntry)] = {
		val fields = entry.fields

		def getStr(fieldName: String): Option[String] = fields.get(fieldName).collect{
			case JsString(s) if !s.trim.isEmpty => s.trim
		}

		for(
			id <- fields.get("Index").collect{case JsNumber(n) => n.toInt};
			variable <- getStr("GrName").orElse(getStr("VarName"));
			qualifier0 <- getStr("QualName").orElse(getStr("VarName"));
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

	def toBadmDate(badmDate: String): BadmDate = badmDate match{

		case badmDateTimeRegex(year, month, day, hour, minute, second_?) =>
			val second = if(second_? == null) "" else ":" + second_?
			BadmLocalDateTime(LocalDateTime.parse(s"$year-$month-${day}T$hour:$minute$second"))

		case badmDateRegex(year, month, day) =>
			BadmLocalDate(LocalDate.parse(s"$year-$month-$day"))

		case yearRegex(year) =>
			BadmYear(year.toInt)

		case _ =>
			throw new ParseException(s"$badmDate is not a valid BADM date string", -1)
	}

	private def toIsoDate(badmDate: String): LocalDate = toBadmDate(badmDate) match {
		case BadmLocalDate(date) => date
		case BadmLocalDateTime(dt) => dt.toLocalDate
		case _ => throw new ParseException(s"$badmDate is not convertible to ISO8601 date", -1)
	}

	private def toValueDate(valueDate: String): Option[BadmDate] =
		if(valueDate == "-9999") None
		else Some(toBadmDate(valueDate))
}
