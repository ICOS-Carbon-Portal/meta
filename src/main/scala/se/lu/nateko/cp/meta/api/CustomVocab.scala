package se.lu.nateko.cp.meta.api

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.XMLSchema

trait CustomVocab {
	protected trait BaseUriProvider{
		def baseUri: String
	}
	def factory: ValueFactory

	protected def makeUriProvider(uri: String) = new BaseUriProvider{
		def baseUri: String = uri
	}

	protected def urlEncode(s: String): String = se.lu.nateko.cp.meta.utils.urlEncode(s)

	def getRelativeRaw(local: String)(implicit bup: BaseUriProvider): IRI = factory.createIRI(bup.baseUri, local)
	def getRelative(local: String)(implicit bup: BaseUriProvider): IRI = getRelativeRaw(urlEncode(local))
	def getRelative(suffix: String, local: String)(implicit bup: BaseUriProvider): IRI = getRelativeRaw(suffix + urlEncode(local))

	def lit(litVal: String, dtype: IRI) = factory.createLiteral(litVal, dtype)
	def lit(litVal: String) = factory.createLiteral(litVal, XMLSchema.STRING)
	def lit(litVal: Int): Literal = lit(litVal.toString, XMLSchema.INTEGER) //INTEGER, not INT!
	def lit(litVal: Long) = factory.createLiteral(litVal)
	def lit(litVal: Boolean) = factory.createLiteral(litVal)
	def lit(litVal: Double) = factory.createLiteral(litVal)
	def lit(litVal: Float) = factory.createLiteral(litVal)
	def lit(litVal: Instant) = factory.createLiteral(litVal.toString, XMLSchema.DATETIME)
	def lit(litVal: LocalDate) = factory.createLiteral(litVal.toString, XMLSchema.DATE)
	def lit(litVal: LocalDateTime) = factory.createLiteral(litVal.toString, XMLSchema.DATETIME)
}