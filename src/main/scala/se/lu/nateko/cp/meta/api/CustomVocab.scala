package se.lu.nateko.cp.meta.api

import java.time.Instant
import java.time.LocalDate

import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.XMLSchema

trait CustomVocab {
	def baseUri: String
	def factory: ValueFactory

	protected def urlEncode(s: String): String = {
		new java.net.URI(null, null, s, null).toASCIIString
	}

	def getRelativeRaw(local: String): IRI = factory.createIRI(baseUri, local)
	def getRelative(local: String): IRI = getRelativeRaw(urlEncode(local))
	def getRelative(suffix: String, local: String): IRI = getRelativeRaw(suffix + urlEncode(local))


	def lit(litVal: String, dtype: IRI) = factory.createLiteral(litVal, dtype)
	def lit(litVal: String) = factory.createLiteral(litVal, XMLSchema.STRING)
	def lit(litVal: Int): Literal = lit(litVal.toString, XMLSchema.INTEGER) //INTEGER, not INT!
	def lit(litVal: Long) = factory.createLiteral(litVal)
	def lit(litVal: Boolean) = factory.createLiteral(litVal)
	def lit(litVal: Double) = factory.createLiteral(litVal)
	def lit(litVal: Float) = factory.createLiteral(litVal)
	def lit(litVal: Instant) = factory.createLiteral(litVal.toString, XMLSchema.DATETIME)
	def lit(litVal: LocalDate) = factory.createLiteral(litVal.toString, XMLSchema.DATE)
}