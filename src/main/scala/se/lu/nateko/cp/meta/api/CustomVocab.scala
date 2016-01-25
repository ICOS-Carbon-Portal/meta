package se.lu.nateko.cp.meta.api

import org.openrdf.model.ValueFactory
import org.openrdf.model.URI
import org.openrdf.model.Literal
import org.openrdf.model.vocabulary.XMLSchema
import java.time.Instant

trait CustomVocab {
	def baseUri: String
	def factory: ValueFactory
	def getRelative(local: String): URI = factory.createURI(baseUri, local)

	def lit(litVal: String, dtype: URI) = factory.createLiteral(litVal, dtype)
	def lit(litVal: String) = factory.createLiteral(litVal, XMLSchema.STRING)
	//important! not INT but INTEGER datatype for integers
	def lit(litVal: Int): Literal = lit(litVal.toString, XMLSchema.INTEGER)
	def lit(litVal: Boolean) = factory.createLiteral(litVal)
	def lit(litVal: Double) = factory.createLiteral(litVal)
	def lit(litVal: Float) = factory.createLiteral(litVal)
	def lit(litVal: Instant) = factory.createLiteral(litVal.toString, XMLSchema.DATETIME)
}