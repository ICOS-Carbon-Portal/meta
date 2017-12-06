package se.lu.nateko.cp.meta.ingestion.badm

import java.time.LocalDate
import java.time.LocalDateTime

import se.lu.nateko.cp.meta.core.etcupload.StationId

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

sealed trait BadmValue{
	def variable: String
	def valueStr: String
}

case class BadmStringValue(variable: String, value: String) extends BadmValue{
	def valueStr = value
}
case class BadmNumericValue(variable: String, valueStr: String, value: Number) extends BadmValue


case class BadmEntry(
	variable: String,
	values: Seq[BadmValue],
	date: Option[BadmDate],
	stationId: Option[StationId],
	submissionDate: LocalDate
)

