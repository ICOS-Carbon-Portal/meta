package se.lu.nateko.cp.meta.api

import java.time.Instant
import java.time.LocalDate

import org.openrdf.model.Literal
import org.openrdf.model.URI
import org.openrdf.model.ValueFactory
import org.openrdf.model.vocabulary.XMLSchema

import akka.http.scaladsl.model.Uri.Path

trait CustomVocab {
	def baseUri: String
	def factory: ValueFactory

	protected def urlEncode(s: String) = Path(s).toString

	def getRelativeRaw(local: String): URI = factory.createURI(baseUri, local)
	def getRelative(local: String): URI = getRelativeRaw(urlEncode(local))
	def getRelative(suffix: String, local: String): URI = getRelativeRaw(suffix + urlEncode(local))


	def lit(litVal: String, dtype: URI) = factory.createLiteral(litVal, dtype)
	def lit(litVal: String) = factory.createLiteral(litVal, XMLSchema.STRING)
	def lit(litVal: Int): Literal = lit(litVal.toString, XMLSchema.INTEGER) //INTEGER, not INT!
	def lit(litVal: Long) = factory.createLiteral(litVal)
	def lit(litVal: Boolean) = factory.createLiteral(litVal)
	def lit(litVal: Double) = factory.createLiteral(litVal)
	def lit(litVal: Float) = factory.createLiteral(litVal)
	def lit(litVal: Instant) = factory.createLiteral(litVal.toString, XMLSchema.DATETIME)
	def lit(litVal: LocalDate) = factory.createLiteral(litVal.toString, XMLSchema.DATE)
}