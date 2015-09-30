package se.lu.nateko.cp.meta.ingestion

import org.openrdf.model.ValueFactory
import org.openrdf.model.URI
import org.openrdf.model.Literal
import org.openrdf.model.vocabulary.XMLSchema

trait CustomVocab{
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
}

class Vocab private(val factory: ValueFactory) extends CustomVocab{ top =>

	val baseUri = "http://meta.icos-cp.eu/ontologies/cpmeta/"

	val atmoStationClass = getRelative("AS")
	val ecoStationClass = getRelative("ES")
	val oceStationClass = getRelative("OS")

	val acquisitionClass = getRelative("DataAcquisition")
	val submissionClass = getRelative("DataSubmission")

	val hasLatitude = getRelative("hasLatitude")
	val hasLongitude = getRelative("hasLongitude")
	val hasName = getRelative("hasName")
	val hasStationId = getRelative("hasStationId")

	val hasSha256sum = getRelative("hasSha256sum")
	val wasSubmittedBy = getRelative("wasSubmittedBy")
	val wasAcquiredBy = getRelative("wasAcquiredBy")

	object qb extends CustomVocab{
		val factory = top.factory
		val baseUri = "http://purl.org/linked-data/cube#"
		val structure = getRelative("structure")
	}

	object prov extends CustomVocab{
		val factory = top.factory
		val baseUri = "http://www.w3.org/ns/prov#"
		val wasAssociatedWith = getRelative("wasAssociatedWith")
		val startedAtTime = getRelative("startedAtTime")
		val endedAtTime = getRelative("endedAtTime")
	}
}

object Vocab{

	import scala.collection.mutable.HashMap

	private[this] val vocabs = HashMap.empty[ValueFactory, Vocab]

	def apply(factory: ValueFactory): Vocab = synchronized{
		vocabs.getOrElseUpdate(factory, new Vocab(factory))
	}

}
