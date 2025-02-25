package se.lu.nateko.cp.meta.api

import java.net.URI
import java.time.{Instant, LocalDate, LocalDateTime}
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.model.{IRI, Literal, ValueFactory}

trait CustomVocab {
	
	def factory: ValueFactory

	protected class BaseUriProvider(val baseUri: String)
	protected def makeUriProvider(uri: String) = new BaseUriProvider(uri)

	def getRelativeRaw(local: String)(using bup: BaseUriProvider): IRI = factory.createIRI(bup.baseUri, local)
	def getRelative(local: UriId)(using BaseUriProvider): IRI = getRelativeRaw(local.urlSafeString)
	def getRelative(suffix: String, local: UriId)(using BaseUriProvider): IRI = getRelativeRaw(suffix + local.urlSafeString)

	def lit(litVal: String, dtype: IRI): Literal = factory.createLiteral(litVal, dtype)
	def lit(litVal: String): Literal = factory.createLiteral(litVal, XSD.STRING)
	def lit(litVal: Int): Literal = lit(litVal.toString, XSD.INTEGER) //INTEGER, not INT!
	def lit(litVal: Long): Literal = factory.createLiteral(litVal)
	def lit(litVal: Boolean): Literal = factory.createLiteral(litVal)
	def lit(litVal: Double): Literal = factory.createLiteral(litVal)
	def lit(litVal: Float): Literal = factory.createLiteral(litVal)
	def lit(litVal: Instant): Literal = factory.createLiteral(litVal.toString, XSD.DATETIME)
	def lit(litVal: LocalDate): Literal = factory.createLiteral(litVal.toString, XSD.DATE)
	def lit(litVal: LocalDateTime): Literal = factory.createLiteral(litVal.toString, XSD.DATETIME)
	def lit(litVal: URI): Literal = factory.createLiteral(litVal.toASCIIString, XSD.ANYURI)
}

object CustomVocab{

	def urlEncode(s: String): String = se.lu.nateko.cp.meta.utils.urlEncode(s)
}

final case class UriId(urlSafeString: String){
	override def toString = urlSafeString
}

object UriId{
	def apply(uri: URI): UriId = UriId(uri.getPath.split('/').last)
	def apply(iri: IRI): UriId = UriId(iri.getLocalName)
	def escaped(str: String): UriId = UriId(CustomVocab.urlEncode(str))
}