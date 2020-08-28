package se.lu.nateko.cp.meta.api

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.XMLSchema

trait CustomVocab {
	import CustomVocab.urlEncode
	def factory: ValueFactory

	protected class BaseUriProvider(val baseUri: String)
	protected def makeUriProvider(uri: String) = new BaseUriProvider(uri)

	def getRelativeRaw(local: String)(implicit bup: BaseUriProvider): IRI = factory.createIRI(bup.baseUri, local)
	def getRelative(local: UriId)(implicit bup: BaseUriProvider): IRI = getRelativeRaw(local.urlSafeString)
	def getRelative(suffix: String, local: UriId)(implicit bup: BaseUriProvider): IRI = getRelativeRaw(suffix + local.urlSafeString)

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

object CustomVocab{

	def urlEncode(s: String) = se.lu.nateko.cp.meta.utils.urlEncode(s)

	//def decodedLocName(iri: IRI) = se.lu.nateko.cp.meta.utils.urlDecode(iri.getLocalName)
}

case class UriId(urlSafeString: String){
	override def toString = urlSafeString
}

object UriId{
	def apply(iri: IRI): UriId = UriId(iri.getLocalName)
	def escaped(str: String) = UriId(CustomVocab.urlEncode(str))
}