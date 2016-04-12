package se.lu.nateko.cp.meta.ingestion.badm

import java.time.LocalDate

case class BadmRawEntry(
	id: Int,
	variable: String,
	qualifier: String,
	value: String,
	valueDate: Option[LocalDate],
	submissionDate: LocalDate)

sealed trait BadmValue{
	def variable: String
}

case class BadmStringValue(variable: String, value: String) extends BadmValue
//case class BadmVocabValue(variable: String, valueIdx: Int) extends BadmValue
case class BadmNumericValue(variable: String, valueStr: String, value: Number) extends BadmValue

case class BadmEntry(variable: String, values: Seq[BadmValue], date: Option[LocalDate], submissionDate: LocalDate)

