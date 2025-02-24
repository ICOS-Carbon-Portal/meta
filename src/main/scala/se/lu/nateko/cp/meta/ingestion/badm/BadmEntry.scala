package se.lu.nateko.cp.meta.ingestion.badm

import se.lu.nateko.cp.meta.core.etcupload.StationId

import java.text.{NumberFormat, ParseException}
import java.time.{LocalDate, LocalDateTime}
import java.util.Locale

sealed trait BadmDate
final case class BadmYear(year: Int) extends BadmDate
final case class BadmLocalDate(date: LocalDate) extends BadmDate
final case class BadmLocalDateTime(dt: LocalDateTime) extends BadmDate

final case class BadmRawEntry(
	id: Int,
	variable: String,
	qualifier: String,
	value: String,
	valueDate: Option[BadmDate],
	submissionDate: LocalDate
)

final case class BadmValue(variable: String, valueStr: String)

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

final case class BadmEntry(
	variable: String,
	values: Seq[BadmValue],
	date: Option[BadmDate],
	stationId: Option[StationId],
	submissionDate: LocalDate
)
