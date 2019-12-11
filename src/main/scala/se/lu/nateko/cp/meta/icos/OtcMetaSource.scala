package se.lu.nateko.cp.meta.icos

import scala.concurrent.duration.DurationInt
import scala.util.Try

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory

import akka.actor.Status
import akka.event.LoggingAdapter
import akka.stream.OverflowStrategy
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.api.CustomVocab
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

class OtcMetaSource(
	server: WriteNotifyingInstanceServer, sparql: SparqlRunner, log: LoggingAdapter
) extends TcMetaSource[OTC.type] {

	private type O = OTC.type
	private val otcVocab = new OtcMetaVocab(server.factory)

	def state: Source[TcState[O], () => Unit] = Source
		.actorRef(1, OverflowStrategy.dropHead)
		.mapMaterializedValue{actor =>
			server.setSubscriber(() => actor ! 2) //cost of single update is 2 units
			() => actor ! Status.Success
		}
		.prepend(Source.single(2)) //triggering initial state reading at the stream startup
		.conflate(Keep.right) //swallow throttle's back-pressure
		.throttle(2, 1.minute, 1, identity, ThrottleMode.Shaping) //2 units of cost per minute
		.mapConcat[TcState[O]]{_ =>
			val stateV = Validated(readState).flatMap(identity)

			if(!stateV.errors.isEmpty){
				val errKind = if(stateV.result.isEmpty) "Hard error" else "Problems"
				log.warning(s"$errKind while reading OTC metadata:\n${stateV.errors.mkString("\n")}")
			}
			stateV.result.toList
		}

	def readState: Validated[TcState[O]] = for(
		people <- getPeople;
		mobStations <- getMobileStations;
		otherOrgs <- getCompsAndInsts;
		orgs = mobStations ++ otherOrgs;
		membs <- getMemberships(orgs, people)
		//TODO Fetch instruments
	) yield new TcState(mobStations.values.toSeq, membs, Nil)

	private def getMobileStations: Validated[Map[IRI, CpMobileStation[O]]] = {
		val q = """prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
		|select distinct ?st ?id ?name ?countryCode where{
		|	values ?mobPlClass {otc:Ship otc:DriftingBuoy}
		|	?plat a ?mobPlClass .
		|	?depl otc:ofPlatform ?plat .
		|	?depl otc:toStation ?st .
		|	#optional {?depl otc:hasEndTime ?endTime}
		|	?st otc:hasStationId ?id .
		|	?st otc:hasName ?name .
		|	optional {?st otc:countryCode ?countryCode }
		|}""".stripMargin

		getLookup(q, "st"){(b, tcId) => CpMobileStation(
			cpId = TcConf.stationId[O](b.getValue("id").stringValue),
			tcId = tcId,
			id = b.getValue("id").stringValue,
			name = b.getValue("name").stringValue,
			country = Option(b.getValue("countryCode")).map(_.stringValue).flatMap(CountryCode.unapply),
			geoJson = None
		)}
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
			cpId = tcId.id,
			tcId = tcId,
			name = b.getValue("name").stringValue,
			label = Option(b.getValue("label")).map(_.stringValue)
		)}
	}

	private def getPeople: Validated[Map[IRI, Person[O]]] = {
		val q = """prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
		|select distinct ?p ?fname ?lname ?email where{
		|	?p a otc:Person .
		|	?p otc:hasFirstName ?fname .
		|	?p otc:hasLastName ?lname .
		|	optional{?p otc:hasEmail ?email }
		|}""".stripMargin

		getLookup(q, "p"){(b, tcId) =>
			val fname = b.getValue("fname").stringValue
			val lname = b.getValue("lname").stringValue
			Person(
				cpId = CpVocab.getPersonCpId(fname, lname),
				tcId = tcId,
				fname = fname,
				lname = lname,
				email = Option(b.getValue("email")).map(_.stringValue)
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
				weight = Option(b.getValue("weight")).map(_.stringValue.toInt)
			)
			Membership[O](
				cpId = tcId.id,
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

}

class OtcMetaVocab(val factory: ValueFactory) extends CustomVocab{

	implicit val bup = makeUriProvider("http://meta.icos-cp.eu/ontologies/otcmeta/")

	// val hasHolder = getRelative("hasHolder")
	// val hasRole = getRelative("hasRole")
	// val atOrganization = getRelative("atOrganization")

	// val spatialReference = getRelative("hasSpatialReference")
	// val hasStartTime = getRelative("hasStartTime")
	// val hasEndTime = getRelative("hasEndTime")

	// val assumedRoleClass = getRelative("AssumedRole")

	object Roles{
		val dataSubmitter = getRelative("dataSubmitter")
		val engineer = getRelative("engineer")
		val pi = getRelative("pi")
		val researcher = getRelative("researcher")

		val map: Map[IRI, Role] = Map(
			dataSubmitter -> DataManager,
			engineer -> Engineer,
			pi -> PI,
			researcher -> Researcher
		)
	}
}

