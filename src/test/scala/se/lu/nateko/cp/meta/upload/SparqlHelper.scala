package se.lu.nateko.cp.meta.upload

import se.lu.nateko.cp.doi.meta._
import scala.concurrent.Future
import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.test.utils.SparqlClient
import java.net.URI

class SparqlHelper(endpoint: URI)(implicit system: ActorSystem){
	import se.lu.nateko.cp.meta.core.sparql._
	import system.dispatcher
	val sparql = new SparqlClient(endpoint)

	def lookupPeopleNames(ids: Seq[URI]): Future[Map[URI, PersonalName]] = {
		val query = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
			select * where{
			values ?p {<${ids.mkString("> <")}>}
			?p cpmeta:hasFirstName ?fname ; cpmeta:hasLastName ?lname .
		}"""
		sparql.select(query).map{qr =>
			qr.results.bindings.map{b =>
				val BoundUri(p) = b("p")
				val BoundLiteral(fname, _) = b("fname")
				val BoundLiteral(lname, _) = b("lname")
				p -> PersonalName(fname, lname)
			}.toMap
		}
	}

	def lookupCpNames(ids: Seq[URI]): Future[Map[URI, String]] = {
		val query = s"""select * where{
			values ?id {<${ids.mkString("> <")}>}
			?id <http://meta.icos-cp.eu/ontologies/cpmeta/hasName> ?name
		}"""
		sparql.select(query).map{qr =>
			qr.results.bindings.map{b =>
				val BoundUri(id) = b("id")
				val BoundLiteral(name, _) = b("name")
				id -> name
			}.toMap
		}
	}

	def emissionInventories: Future[Seq[URI]] = {
		val query = """prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|select * where{
		|	?dobj cpmeta:hasObjectSpec <http://meta.icos-cp.eu/resources/cpmeta/co2EmissionInventory>
		|}"""
		sparql.select(query).map{qr =>
			qr.results.bindings.map{b =>
				val BoundUri(dobj) = b("dobj")
				dobj
			}
		}
	}
}
