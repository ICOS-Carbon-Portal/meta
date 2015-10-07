package se.lu.nateko.cp.meta.ingestion

import org.openrdf.model.Statement
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import org.openrdf.model.ValueFactory
import org.openrdf.model.URI
import java.net.URLEncoder
import org.openrdf.model.vocabulary.RDF
import se.lu.nateko.cp.meta.utils.sesame.Loading
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer

object StationStructuringIngester{

	def apply(firstStage: Ingester) = new Ingester{

		override def getStatements(factory: ValueFactory): Iterator[Statement] = {
			val repo = Loading.fromStatements(firstStage.getStatements(factory))
			val server = new SesameInstanceServer(repo)
			new StationStructuring(server).getStatements
		}

	}

}

class StationStructuring(plainStations: InstanceServer){

	case class RawPi(name: String, email: String)
	case class Pi(uri: URI, fname: Option[String], lname: String, email: String, affiliation: Option[String])

	val vocab = new StationStructuringVocab(plainStations.factory)
	import vocab._

	def getStatements: Iterator[Statement] = {
		val piStats = stationsAndPis.iterator.flatMap{
			case (station, pis) => stationPiStatements(station, pis.iterator)
		}
		val blackList = Seq(hasPiName, hasPiEmail)

		val originalStats = plainStations.getStatements(None, None, None)
			.filterNot(st => blackList.contains(st.getPredicate))

		piStats ++ originalStats
	}

	def stationPiStatements(station: URI, pis: Iterator[Pi]) = pis.flatMap{pi =>
		Iterator(
			factory.createStatement(station, hasPi, pi.uri),
			factory.createStatement(pi.uri, RDF.TYPE, PI)
		) ++ piStatements(pi)
	}

	def piStatements(pi: Pi): Iterator[Statement] = Iterator(
			(hasFirstName, pi.fname),
			(hasLastName, Some(pi.lname)),
			(hasEmail, Some(pi.email)),
			(hasAffiliation, pi.affiliation)
		).map{
			case (pred, obj) => obj.map(lit).map(factory.createStatement(pi.uri, pred, _))
		}.flatten

	def stationValuePairs(prop: URI): Iterator[(URI, String)] = plainStations
		.getStatements(None, Some(prop), None)
		.map(st => (st.getSubject.asInstanceOf[URI], st.getObject.stringValue))

	def stationsAndPis: Map[URI, Seq[Pi]] = {
		val rawNames: Seq[(URI, String)] = stationValuePairs(hasPiName).toSeq.distinct
		val rawEmails: Map[URI, String] = stationValuePairs(hasPiEmail).toSeq.distinct.toMap

		rawNames.map{ case (station, rawName) =>
			val rawEmail = rawEmails(station)
			val rawPi = RawPi(rawName, rawEmail)
			val pis = expandPi(rawPi)
			(station, pis)
		}.toMap
	}

	def expandPi(raw: RawPi): Seq[Pi] = raw.name.split(';')
		.zip(raw.email.split(';'))
		.map{
			case (name, email) => singlePi(name.trim, email.trim.toLowerCase)
		}
	
	def singlePi(rawName: String, email: String): Pi = {
		val (fullName, affiliation) = rawName.split(",", 2).toSeq match{
			case Seq(name, affil) => (name.trim, Some(affil.trim))
			case Seq(name) => (name.trim, None)
		}
		
		val (fname, lname) = fullName.split("\\s", 2).toSeq match{
			case Seq(fname, lname) => (Some(fname.trim), lname.trim)
			case Seq(lname) => (None, lname.trim)
		}

		Pi(piUri(email), fname, lname, email, affiliation)
	}
}

class StationStructuringVocab(factory: ValueFactory) extends StationsVocab(factory){
	val hasPi = getRelative("hasPi")
	val hasFirstName = getRelative("hasFirstName")
	val hasLastName = getRelative("hasLastName")
	val hasEmail = getRelative("hasEmail")
	val hasAffiliation = getRelative("hasAffiliation")
	val hasAssociatedFile = getRelative("hasAssociatedFile")
	val PI = getRelative("PI")

	val hasAddress= getRelative("hasAddress")
	val hasWebsite= getRelative("hasWebsite")
	val hasAccessibility= getRelative("hasAccessibility")
	val hasVegetation= getRelative("hasVegetation")
	val hasAnthropogenics= getRelative("hasAnthropogenics")
	val hasConstructionStartDate= getRelative("hasConstructionStartDate")
	val hasConstructionEndDate= getRelative("hasConstructionEndDate")
	val hasTelecom= getRelative("hasTelecom")
	val hasExistingInfrastructure= getRelative("hasExistingInfrastructure")

	val files = new FilesVocab(factory)

	def piUri(email: String) = getRelative("PI/" + URLEncoder.encode(email, "UTF-8"))
}


class FilesVocab(val factory: ValueFactory) extends CustomVocab{
	val baseUri = "http://meta.icos-cp.eu/files/"

	val hasType = getRelative("hasType")
	val hasName = getRelative("hasName")

	def getUri(hashsum: String) = getRelative(hashsum)
}
