package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.Person
import java.time.Instant
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.services.upload.CpmetaFetcher
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import java.{util => ju}
import se.lu.nateko.cp.meta.api.SparqlQuery
import org.eclipse.rdf4j.query.BindingSet
import java.net.URI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.vocabulary.XMLSchema

class AttributionProvider(repo: Repository) extends CpmetaFetcher{
	import AttributionProvider._

	override protected val server = new Rdf4jInstanceServer(repo)
	private val sparql = new Rdf4jSparqlRunner(repo)

	def getAuthors(dobj: DataObject): Seq[Person] = (
		for(
			l2 <- dobj.specificInfo.toOption;
			prodTime <- productionTime(dobj)
		) yield{
			val query = membsQuery(l2.acquisition.station.org.self.uri)
			sparql.evaluateTupleQuery(SparqlQuery(query))
				.map(parseMembership)
				.filter(_.isRelevantAt(prodTime))
				.toSeq
				.sorted
				.map(_.person)
		}
	).toSeq.flatten

	def membsQuery(station: URI) = s"""select ?person ?weight ?start ?end where{
		|	?memb <${metaVocab.atOrganization}> <$station> .
		|	?person <${metaVocab.hasMembership}> ?memb .
		|	OPTIONAL{?memb <${metaVocab.hasAttributionWeight}> ?weight }
		|	OPTIONAL{?memb <${metaVocab.hasStartTime}> ?start }
		|	OPTIONAL{?memb <${metaVocab.hasEndTime}> ?end }
		|}""".stripMargin

	def parseMembership(bs: BindingSet): Membership = {
		val person = getPerson(bs.getValue("person").asInstanceOf[IRI])
		val start = getOptInstant(bs, "start")
		val end = getOptInstant(bs, "end")
		val weight = getOptInt(bs, "weight")
		new Membership(person, start, end, weight)
	}
}

object AttributionProvider{

	def productionTime(dobj: DataObject): Option[Instant] =
		dobj.production.map(_.dateTime).orElse{
			dobj.specificInfo.toOption.flatMap(_.acquisition.interval).map(_.stop)
		}

	class Membership(val person: Person, start: Option[Instant], end: Option[Instant], val weight: Option[Int]){
		def isRelevantAt(time: Instant): Boolean =
			start.map(s => s.compareTo(time) < 0).getOrElse(true) &&
			end.map(e => e.compareTo(time) > 0).getOrElse(true)
	}

	implicit val personOrdering: ju.Comparator[Person] = Ordering
		.by[Person, String](_.lastName)
		.thenComparing(Ordering.by[Person, String](_.firstName))

	implicit val membershipOrdering: ju.Comparator[Membership] = Ordering
		.by((m: Membership) => m.weight)
		.reverse
		.thenComparing(Ordering.by[Membership, Person](_.person))

	def getOptLiteral(bs: BindingSet, name: String, litType: IRI): Option[Literal] = Option(bs.getValue(name)).collect{
		case lit: Literal if(lit.getDatatype == litType) => lit
	}

	def getOptInstant(bs: BindingSet, name: String): Option[Instant] = getOptLiteral(bs, name, XMLSchema.DATETIME)
		.map(lit => Instant.parse(lit.stringValue))

	def getOptInt(bs: BindingSet, name: String): Option[Int] = getOptLiteral(bs, name, XMLSchema.INTEGER)
		.map(lit => lit.intValue)
}
