package se.lu.nateko.cp.meta.ingestion.badm

import java.text.NumberFormat
import java.text.ParseException
import java.time.LocalDate
import java.util.Locale

import scala.annotation.migration
import scala.io.Source

import BadmConsts._
import java.io.InputStreamReader
import com.opencsv.CSVReader

object Parser {

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

	def csvRowToRawEntry(cells: Array[String]): BadmRawEntry = {
		val variable = cells(1)

		val (qualifier, value) = {
			val value = cells(3)
			cells(2) match {
				case ValueQualifier => (variable, value)
				case VariableVar => value match {
					case varCodeRegex(varCode) => (VarCodeVar, varCode)
				}
				case PercentQualifier => (variable + "_" + PercentQualifier, value)
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

	def isDate(entry: BadmRawEntry): Boolean = entry.qualifier == DateQualifier

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

	def parseEntriesFromCsv(src: java.io.InputStream): Seq[BadmEntry] = {
		aggregateEntries(getCsvRows(src).map(csvRowToRawEntry).toSeq)
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

}
