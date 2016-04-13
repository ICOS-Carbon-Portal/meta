package se.lu.nateko.cp.meta.ingestion.badm

import java.time.LocalDate

sealed trait BadmDate
case class BadmYear(year: Int) extends BadmDate
case class BadmLocalDate(date: LocalDate) extends BadmDate

case class BadmRawEntry(
	id: Int,
	variable: String,
	qualifier: String,
	value: String,
	valueDate: Option[BadmDate],
	submissionDate: LocalDate)

sealed trait BadmValue{
	def variable: String
}

case class BadmStringValue(variable: String, value: String) extends BadmValue
//case class BadmVocabValue(variable: String, valueIdx: Int) extends BadmValue
case class BadmNumericValue(variable: String, valueStr: String, value: Number) extends BadmValue


case class BadmEntry(variable: String, values: Seq[BadmValue], date: Option[BadmDate], submissionDate: LocalDate)

