package se.lu.nateko.cp.meta.icos

import scala.concurrent.duration.DurationInt
import scala.util.Try

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory

import akka.actor.ActorRef
import akka.actor.Status
import akka.event.LoggingAdapter
import akka.stream.OverflowStrategy
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.api.{CustomVocab, UriId}
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import se.lu.nateko.cp.meta.instanceserver.WriteNotifyingInstanceServer
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.api.SparqlQuery
import org.eclipse.rdf4j.query.BindingSet
import se.lu.nateko.cp.meta.services.CpVocab
import org.eclipse.rdf4j.model.Value
import java.time.Instant
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.data.Orcid

class OtcMetaSource(
	server: WriteNotifyingInstanceServer, sparql: SparqlRunner, val log: LoggingAdapter
) extends TriggeredMetaSource[OTC.type] {

	private type O = OTC.type
	private val otcVocab = new OtcMetaVocab(server.factory)

	override def registerListener(actor: ActorRef): Unit = {
		server.setSubscriber(() => actor ! 1)
	}

	override def readState: Validated[State] = for(
		people <- getPeople;
		mobStations <- getMobileStations;
		statStations <- getStationaryStations;
		otherOrgs <- getCompsAndInsts;
		orgs = mobStations ++ statStations ++ otherOrgs;
		membs <- getMemberships(orgs, people)
		//TODO Fetch instruments
	) yield new TcState(mobStations.values.toSeq, membs, Nil)

	private def getMobileStations: Validated[Map[IRI, TcMobileStation[O]]] = {
		val q = """prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
		|select distinct ?st ?id ?name ?countryCode where{
		|	?st otc:hasStationId ?id .
		|	filter exists{
		|		?depl otc:toStation ?st ; otc:ofPlatform ?plat .
		|		values ?mobPlClass {otc:Ship otc:DriftingBuoy}
		|		?plat a ?mobPlClass .
		|	}
		|	?st otc:hasName ?name .
		|	optional {?st otc:countryCode ?countryCode }
		|}""".stripMargin

		getLookup(q, "st"){(b, tcId) => TcMobileStation(
			cpId = stationId(b.getValue("id").stringValue),
			tcId = tcId,
			id = b.getValue("id").stringValue,
			name = b.getValue("name").stringValue,
			country = Option(b.getValue("countryCode")).map(_.stringValue).flatMap(CountryCode.unapply),
			geoJson = None
		)}
	}

	//TODO Rewrite to allow for multiple platform deployments, picking the latest of them for lat/lon
	private def getStationaryStations: Validated[Map[IRI, TcStationaryStation[O]]] = {
		val q = """prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
		|select distinct ?st ?id ?name ?lat ?lon ?countryCode where{
		|	?plat a otc:Mooring .
		|	?depl otc:ofPlatform ?plat ; otc:toStation ?st .
		|	?plat otc:hasLatitude ?lat ; otc:hasLongitude ?lon .
		|	#optional {?depl otc:hasEndTime ?endTime}
		|	?st otc:hasStationId ?id ; otc:hasName ?name .
		|	optional {?st otc:countryCode ?countryCode }
		|}""".stripMargin

		getLookup(q, "st"){(b, tcId) =>
			val pos = Position(parseDouble(b.getValue("lat")), parseDouble(b.getValue("lon")), None)
			TcStationaryStation(
				cpId = stationId(b.getValue("id").stringValue),
				tcId = tcId,
				id = b.getValue("id").stringValue,
				name = b.getValue("name").stringValue,
				pos = pos,
				country = Option(b.getValue("countryCode")).map(_.stringValue).flatMap(CountryCode.unapply),
			)
		}
	}

	private def getCompsAndInsts: Validated[Map[IRI, CompanyOrInstitution[O]]] = {
		val q = """prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
		|select distinct ?org ?name ?label where{
		|	values ?orgClass {otc:CommercialCompany otc:AcademicInstitution}
		|	?org a ?orgClass .
		|	?org otc:hasName ?name .
		|	optional{?org rdfs:label ?label }
		|}""".stripMargin

		getLookup(q, "org"){(b, tcId) => CompanyOrInstitution(
			cpId = UriId(tcId.id),
			tcIdOpt = Some(tcId),
			name = b.getValue("name").stringValue,
			label = Option(b.getValue("label")).map(_.stringValue)
		)}
	}

	private def getPeople: Validated[Map[IRI, Person[O]]] = {
		val q = """prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
		|select distinct * where{
		|	?p a otc:Person .
		|	?p otc:hasFirstName ?fname .
		|	?p otc:hasLastName ?lname .
		|	optional{?p otc:hasEmail ?email }
		|	optional{?p otc:hasOrcidId ?orcid }
		|}""".stripMargin

		getLookup(q, "p"){(b, tcId) =>
			val fname = b.getValue("fname").stringValue
			val lname = b.getValue("lname").stringValue
			Person(
				cpId = CpVocab.getPersonCpId(fname, lname),
				tcIdOpt = Some(tcId),
				fname = fname,
				lname = lname,
				email = Option(b.getValue("email")).map(_.stringValue.toLowerCase),
				orcid = Option(b.getValue("orcid")).flatMap(v => Orcid.unapply(v.stringValue))
			)
		}
	}
	private def getLookup[T](query: String, entVar: String)(maker: (BindingSet, TcId[O]) => T): Validated[Map[IRI, T]] = {
		Validated(sparql.evaluateTupleQuery(SparqlQuery(query))).flatMap{iter =>
			val entValids = iter.toIndexedSeq.map{b =>
				Validated{
					val entIri = b.getValue(entVar).asInstanceOf[IRI]
					val ent = maker(b, TcConf.makeId[O](entIri.getLocalName))
					entIri -> ent
				}
			}
			Validated.sequence(entValids).map(_.toMap)
		}
	}

	private def getMemberships(orgs: Map[IRI, Organization[O]], pers: Map[IRI, Person[O]]): Validated[Seq[Membership[O]]] = {
		val q = """prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
		|select * where{
		|	?role a otc:AssumedRole .
		|	?role otc:hasRole ?roleKind .
		|	?role otc:atOrganization ?org .
		|	?role otc:hasHolder ?person .
		|	optional{?role otc:hasAttributionWeight ?weight}
		|	optional{?role otc:hasStartTime ?start}
		|	optional{?role otc:hasEndTime ?end}
		|}""".stripMargin

		getLookup(q, "role"){(b, tcId) =>
			val role = new AssumedRole[O](
				kind = otcVocab.Roles.map(b.getValue("roleKind").asInstanceOf[IRI]),
				holder = pers(b.getValue("person").asInstanceOf[IRI]),
				org = orgs(b.getValue("org").asInstanceOf[IRI]),
				weight = Option(b.getValue("weight")).map(_.stringValue.toInt),
				extra = None
			)
			Membership[O](
				cpId = UriId(tcId.id),
				role = role,
				start = parseDate(b.getValue("start")),
				stop = parseDate(b.getValue("end"))
			)
		}.map(_.values.toSeq)
	}

	private def parseDate(v: Value): Option[Instant] = v match {
		case lit: Literal if lit.getDatatype == XMLSchema.DATE =>
			Some(Instant.parse(lit.stringValue + "T12:00:00Z"))
		case _ => None
	}

	private def parseDouble(v: Value): Double = v match {
		case lit: Literal if lit.getDatatype == XMLSchema.DOUBLE =>
			lit.stringValue.toDouble
		case _ =>
			throw new NumberFormatException(s"Expected $v to be a RDF literal of type xsd:double")
	}

}

class OtcMetaVocab(val factory: ValueFactory) extends CustomVocab{

	implicit val bup = makeUriProvider("http://meta.icos-cp.eu/ontologies/otcmeta/")

	// val hasHolder = getRelativeRaw("hasHolder")
	// val hasRole = getRelativeRaw("hasRole")
	// val atOrganization = getRelativeRaw("atOrganization")

	// val spatialReference = getRelativeRaw("hasSpatialReference")
	// val hasStartTime = getRelativeRaw("hasStartTime")
	// val hasEndTime = getRelativeRaw("hasEndTime")

	// val assumedRoleClass = getRelativeRaw("AssumedRole")

	object Roles{
		val dataSubmitter = getRelativeRaw("dataSubmitter")
		val engineer = getRelativeRaw("engineer")
		val pi = getRelativeRaw("pi")
		val researcher = getRelativeRaw("researcher")

		val map: Map[IRI, Role] = Map(
			dataSubmitter -> DataManager,
			engineer -> Engineer,
			pi -> PI,
			researcher -> Researcher
		)
	}
}

