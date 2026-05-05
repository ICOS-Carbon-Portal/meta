package se.lu.nateko.cp.meta.ingestion.badm

import scala.language.unsafeNulls

import se.lu.nateko.cp.meta.core.etcupload.StationId

import java.text.{NumberFormat, ParseException}
import java.time.{LocalDate, LocalDateTime}
import java.util.Locale

sealed trait BadmDate
case class BadmYear(year: Int) extends BadmDate
case class BadmLocalDate(date: LocalDate) extends BadmDate
case class BadmLocalDateTime(dt: LocalDateTime) extends BadmDate

case class BadmRawEntry(
	id: Int,
	variable: String,
	qualifier: String,
	value: String,
	valueDate: Option[BadmDate],
	submissionDate: LocalDate
)

case class BadmValue(variable: String, valueStr: String)

object Badm{

	sealed trait Extractor[T]{
		def unapply(v: String): Option[T]
	}

	val numParser = NumberFormat.getNumberInstance(Locale.ROOT)

	object Numeric extends Extractor[Number]{
		override def unapply(v: String): Option[Number] = try{
			Some(numParser.parse(v))
		}catch{
			case _: ParseException => None
		}
	}

	object Date extends Extractor[BadmDate]{
		override def unapply(v: String): Option[BadmDate] = try{
			Some(Parser.toBadmDate(v))
		}catch{
			case _: ParseException => None
		}
	}
}

case class BadmEntry(
	variable: String,
	values: Seq[BadmValue],
	date: Option[BadmDate],
	stationId: Option[StationId],
	submissionDate: LocalDate
)
