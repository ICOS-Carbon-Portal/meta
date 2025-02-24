package se.lu.nateko.cp.meta.api

import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.model.{IRI, Literal, ValueFactory}

import java.net.URI
import java.time.{Instant, LocalDate, LocalDateTime}

trait CustomVocab {
	
	def factory: ValueFactory

	protected class BaseUriProvider(val baseUri: String)
	protected def makeUriProvider(uri: String) = new BaseUriProvider(uri)

	def getRelativeRaw(local: String)(using bup: BaseUriProvider): IRI = factory.createIRI(bup.baseUri, local)
	def getRelative(local: UriId)(using BaseUriProvider): IRI = getRelativeRaw(local.urlSafeString)
	def getRelative(suffix: String, local: UriId)(using BaseUriProvider): IRI = getRelativeRaw(suffix + local.urlSafeString)

	def lit(litVal: String, dtype: IRI) = factory.createLiteral(litVal, dtype)
	def lit(litVal: String) = factory.createLiteral(litVal, XSD.STRING)
	def lit(litVal: Int): Literal = lit(litVal.toString, XSD.INTEGER) //INTEGER, not INT!
	def lit(litVal: Long) = factory.createLiteral(litVal)
	def lit(litVal: Boolean) = factory.createLiteral(litVal)
	def lit(litVal: Double) = factory.createLiteral(litVal)
	def lit(litVal: Float) = factory.createLiteral(litVal)
	def lit(litVal: Instant) = factory.createLiteral(litVal.toString, XSD.DATETIME)
	def lit(litVal: LocalDate) = factory.createLiteral(litVal.toString, XSD.DATE)
	def lit(litVal: LocalDateTime) = factory.createLiteral(litVal.toString, XSD.DATETIME)
	def lit(litVal: URI) = factory.createLiteral(litVal.toASCIIString, XSD.ANYURI)
}

object CustomVocab{

	def urlEncode(s: String) = se.lu.nateko.cp.meta.utils.urlEncode(s)
}

final case class UriId(urlSafeString: String){
	override def toString = urlSafeString
}

object UriId{
	def apply(uri: URI): UriId = UriId(uri.getPath.split('/').last)
	def apply(iri: IRI): UriId = UriId(iri.getLocalName)
	def escaped(str: String) = UriId(CustomVocab.urlEncode(str))
}