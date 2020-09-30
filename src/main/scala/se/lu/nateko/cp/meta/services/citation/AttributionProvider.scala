package se.lu.nateko.cp.meta.services.citation

import java.net.URI
import java.time.Instant
import java.{util => ju}

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.Repository

import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.Person
import se.lu.nateko.cp.meta.core.data.UriResource
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.core.data.Orcid

class AttributionProvider(repo: Repository){
	import AttributionProvider._

	private val sparql = new Rdf4jSparqlRunner(repo)
	private val metaVocab = new CpmetaVocab(repo.getValueFactory)

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

	private def membsQuery(station: URI) = s"""select distinct ?person ?fname ?lname ?orcid ?weight ?start ?end where{
		|	?memb <${metaVocab.atOrganization}> <$station> .
		|	?person <${metaVocab.hasMembership}> ?memb ;
		|		<${metaVocab.hasFirstName}> ?fname ;
		|		<${metaVocab.hasLastName}> ?lname .
		|	OPTIONAL{?person <${metaVocab.hasOrcidId}> ?orcid }
		|	OPTIONAL{?memb <${metaVocab.hasAttributionWeight}> ?weight }
		|	OPTIONAL{?memb <${metaVocab.hasStartTime}> ?start }
		|	OPTIONAL{?memb <${metaVocab.hasEndTime}> ?end }
		|}""".stripMargin

	private def parseMembership(bs: BindingSet) = new Membership(
		parsePerson(bs),
		getOptInstant(bs, "start"),
		getOptInstant(bs, "end"),
		getOptInt(bs, "weight")
	)

	private def parsePerson(bs: BindingSet) = Person(
		UriResource(bs.getValue("person").asInstanceOf[IRI].toJava, None, Nil),
		bs.getValue("fname").stringValue,
		bs.getValue("lname").stringValue,
		Option(bs.getValue("orcid")).flatMap(v => Orcid.unapply(v.stringValue))
	)
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
