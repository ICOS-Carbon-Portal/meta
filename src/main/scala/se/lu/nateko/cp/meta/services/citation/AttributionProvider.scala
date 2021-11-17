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
import se.lu.nateko.cp.meta.core.data.DataTheme
import se.lu.nateko.cp.meta.core.data.Orcid
import se.lu.nateko.cp.meta.core.data.Person
import se.lu.nateko.cp.meta.core.data.UriResource
import se.lu.nateko.cp.meta.core.data.Station
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.icos.Role
import se.lu.nateko.cp.meta.icos.PI
import se.lu.nateko.cp.meta.icos.Administrator
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.upload.CpmetaFetcher
import scala.util.Try
import scala.util.Using

final class AttributionProvider(repo: Repository, vocab: CpVocab) extends CpmetaFetcher{
	import AttributionProvider._

	override val server = new Rdf4jInstanceServer(repo)
	private val sparql = new Rdf4jSparqlRunner(repo)

	def getAuthors(dobj: DataObject): Seq[Person] = dobj.specificInfo.fold[Seq[Person]](
		_ => Nil,
		l2 => getMemberships(l2.acquisition.station)
			.filter(getTcSpecificFilter(dobj))
			.filter(_.isRelevantFor(dobj))
			.toIndexedSeq
			.sorted
			.map(_.person)
			.distinct
	)

	def getMemberships(station: Station): IndexedSeq[Membership] = {
		val query = membsQuery(station.org.self.uri)
		Using(sparql.evaluateTupleQuery(SparqlQuery(query)))(_.toIndexedSeq).get
			.flatMap(parseMembership)
	}

	private def membsQuery(station: URI) = s"""select distinct ?person ?role ?weight ?extra ?start ?end where{
		|	?memb <${metaVocab.atOrganization}> <$station> ;
		|		<${metaVocab.hasRole}> ?role .
		|	?person <${metaVocab.hasMembership}> ?memb .
		|	OPTIONAL{?memb <${metaVocab.hasAttributionWeight}> ?weight }
		|	OPTIONAL{?memb <${metaVocab.hasExtraRoleInfo}> ?extra }
		|	OPTIONAL{?memb <${metaVocab.hasStartTime}> ?start }
		|	OPTIONAL{?memb <${metaVocab.hasEndTime}> ?end }
	|}""".stripMargin

	private def parseMembership(bs: BindingSet): Option[Membership] = for(
		role <- parseRole(bs);
		person <- parsePerson(bs)
	) yield Membership(
		person,
		role,
		getOptInstant(bs, "start"),
		getOptInstant(bs, "end"),
		getOptInt(bs, "weight"),
		getOptLiteral(bs, "extra", XMLSchema.STRING).map(_.stringValue)
	)

	private def parsePerson(bs: BindingSet) = Option(bs.getValue("person")).collect{
		case iri: IRI => Try{getPerson(iri)}.toOption
	}.flatten

	private def parseRole(bs: BindingSet): Option[UriResource] = Option(bs.getValue("role")).collect{
		case iri: IRI => getLabeledResource(iri)
	}

	private def getTcSpecificFilter(dobj: DataObject): Membership => Boolean =
		if(dobj.specification.theme.self.uri === vocab.atmoTheme) memb => (memb.weight.isDefined && {
			val speciesOk = for(
				extra <- memb.extra;
				l2 <- dobj.specificInfo.toOption;
				cols <- l2.columns
			) yield{
				val colLabels = cols.map(_.label.toLowerCase)
				extra.split(',').map(_.trim.toLowerCase).exists(species =>
					colLabels.exists(_.contains(species)) ||
					dobj.specification.self.label.getOrElse("").toLowerCase.contains(species)
				)
			}
			speciesOk.getOrElse(true)
		}) else
			_ => true
}

object AttributionProvider{

	case class Membership(person: Person, role: UriResource, start: Option[Instant], end: Option[Instant], weight: Option[Int], extra: Option[String]){
		def isRelevantFor(dobj: DataObject): Boolean = dobj.specificInfo.fold(
			l3 => {
				val prodTime = l3.productionInfo.dateTime
				start.map(s => s.compareTo(prodTime) < 0).getOrElse(true) &&
				end.map(e => e.compareTo(prodTime) > 0).getOrElse(true)
			},
			l2 => l2.acquisition.interval.fold(true){acqInt =>
				start.map(s => s.compareTo(acqInt.stop) < 0).getOrElse(true) &&
				end.map(e => e.compareTo(acqInt.start) > 0).getOrElse(true)
			}
		)
	}

	implicit val personOrdering: ju.Comparator[Person] = Ordering
		.by[Person, String](_.lastName.toUpperCase)
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
