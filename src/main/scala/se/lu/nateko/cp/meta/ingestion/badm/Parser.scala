package se.lu.nateko.cp.meta.ingestion.badm

import java.text.NumberFormat
import java.text.ParseException
import java.time.LocalDate
import java.util.Locale

import scala.annotation.migration
import scala.io.Source

object Parser {

	private[this] val badmDateRegex = """(\d{4})(\d\d)(\d\d)""".r
	private[this] val yearRegex = "(\\d{4})".r
	private[this] val varCodeRegex = "(.+)_\\d+_\\d+_\\d+".r

	def toBadmDate(badmDate: String): BadmDate = {
		badmDate match{
			case badmDateRegex(year, month, day) =>
				BadmLocalDate(LocalDate.parse(s"$year-$month-$day"))
			case yearRegex(year) =>
				BadmYear(year.toInt)
			case _ =>
				throw new ParseException(s"$badmDate is not a valid BADM date string", -1)
		}
	}
	
	def toIsoDate(badmDate: String): LocalDate = toBadmDate(badmDate) match {
		case BadmLocalDate(date) => date
		case _ => throw new ParseException(s"$badmDate is not convertible to ISO8601 date", -1)
	}

	def csvRowToRawEntry(row: String): BadmRawEntry = {
		val cells = rowToCells(row)

		val variable = cells(1)

		val (qualifier, value) = {
			val value = cells(3)
			cells(2) match {
				case "VALUE" => (variable, value)
				case "VARIABLE_H_V_R" => value match {
					case varCodeRegex(varCode) => ("VAR_CODE", varCode)
				}
				case qual => (qual, value)
			}
		}

		val valueDate = {
			val date = cells(4)
			if(date == "-9999") None
			else Some(toBadmDate(date))
		}

		BadmRawEntry(
			id = cells(0).toInt,
			variable = variable,
			qualifier = qualifier,
			value = value,
			valueDate =  valueDate,
			submissionDate = toIsoDate(cells(5))
		)
	}

	def isDate(entry: BadmRawEntry): Boolean = entry.qualifier == "DATE"

	def aggregateEntries(raw: Seq[BadmRawEntry]): Seq[BadmEntry] = {

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
		raw.filterNot(isDate).groupBy(_.id).toSeq.sortBy(_._1).map(_._2).map(fromSameId).toSeq
	}

	def parseEntriesFromCsv(src: Source): Seq[BadmEntry] = {
		aggregateEntries(src.getLines().drop(1).map(csvRowToRawEntry).toSeq)
	}

	def rowToCells(row: String): Array[String] = row.split(',').map(_.trim)

}
