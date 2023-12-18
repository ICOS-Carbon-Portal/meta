package se.lu.nateko.cp.meta.services.citation

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.query.BindingSet
import se.lu.nateko.cp.meta.api.RdfLens.MetaConn
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.data.Agent
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.DataTheme
import se.lu.nateko.cp.meta.core.data.Orcid
import se.lu.nateko.cp.meta.core.data.Organization
import se.lu.nateko.cp.meta.core.data.Person
import se.lu.nateko.cp.meta.core.data.Station
import se.lu.nateko.cp.meta.core.data.UriResource
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.metaflow.Administrator
import se.lu.nateko.cp.meta.metaflow.PI
import se.lu.nateko.cp.meta.metaflow.Role
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.services.upload.CpmetaReader
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import java.time.Instant
import java.{util => ju}
import scala.collection.mutable
import scala.util.Try
import scala.util.Using

final class AttributionProvider(vocab: CpVocab, val metaVocab: CpmetaVocab) extends CpmetaReader:
	import AttributionProvider.*
	import TriplestoreConnection.{TSC2V, getLabeledResource}

	def getAuthors(dobj: DataObject)(using MetaConn): Validated[Seq[Person]] = dobj.specificInfo.fold(
		_ => Validated.ok(Nil),
		l2 => getMemberships(l2.acquisition.station.org.self.uri).map(
			_.filter(getTcSpecificFilter(dobj))
			.filter(_.role.isRelevantFor(dobj))
			.toIndexedSeq
			.sorted
			.map(_.person)
			.distinct
		)
	)

	def getMemberships(org: URI)(using MetaConn): Validated[IndexedSeq[Membership]] = sparqlAndParse(
			s"""select distinct ?person $roleDetailsVars where{
			|	?memb <${metaVocab.atOrganization}> <$org> .
			|	?person <${metaVocab.hasMembership}> ?memb .
			|	$roleDetailsQuerySegment
			|}""".stripMargin
		): bs =>
			for
				person <- parsePerson(bs)
				role <- parseRoleDetails(bs)
			yield Membership(person, role)


	def getPersonRoles(person: URI): TSC2V[IndexedSeq[PersonRole]] = sparqlAndParse(
			s"""select distinct ?org $roleDetailsVars where{
			|	<$person> <${metaVocab.hasMembership}> ?memb .
			|	?memb <${metaVocab.atOrganization}> ?org .
			|	$roleDetailsQuerySegment
			|}""".stripMargin
		): bs =>
			for
				org <- parseUriRes(bs, "org")
				role <- parseRoleDetails(bs)
			yield PersonRole(org, role)


	private def sparqlAndParse[T](query: String)(parser: BindingSet => Validated[T]): TSC2V[IndexedSeq[T]] = conn ?=>
		val respTry = Using(conn.evaluateTupleQuery(SparqlQuery(query)))(_.toIndexedSeq)
		Validated.fromTry(respTry).flatMap:
			bindings => Validated.sequence(bindings.map(parser))

	private val roleDetailsVars = "?role ?weight ?extra ?start ?end"
	private val roleDetailsQuerySegment = s"""?memb <${metaVocab.hasRole}> ?role .
		|	OPTIONAL{?memb <${metaVocab.hasAttributionWeight}> ?weight }
		|	OPTIONAL{?memb <${metaVocab.hasExtraRoleInfo}> ?extra }
		|	OPTIONAL{?memb <${metaVocab.hasStartTime}> ?start }
		|	OPTIONAL{?memb <${metaVocab.hasEndTime}> ?end }""".stripMargin

	private def parseRoleDetails(bs: BindingSet): TSC2V[RoleDetails] = parseUriRes(bs, "role").map:
		role => RoleDetails(
			role,
			getOptInstant(bs, "start"),
			getOptInstant(bs, "end"),
			getOptInt(bs, "weight"),
			getOptLiteral(bs, "extra", XSD.STRING).map(_.stringValue)
		)


	private def parsePerson(bs: BindingSet)(using MetaConn): Validated[Person] =
		Validated(bs.getValue("person")).require("no person found").flatMap:
			case iri: IRI => getPerson(iri)
			case other => Validated.error(s"Expected a person's URI, got $other")

	private def parseUriRes(bs: BindingSet, varName: String): TSC2V[UriResource] =
		Validated(bs.getValue(varName)).flatMap:
			case iri: IRI => getLabeledResource(iri)
			case other => Validated.error(s"Expected a URI, got $other")

	private def getTcSpecificFilter(dobj: DataObject): Membership => Boolean =
		if(dobj.specification.theme.self.uri === vocab.atmoTheme) memb => (memb.role.weight.isDefined && {
			val speciesOk = for(
				extra <- memb.role.extra;
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
end AttributionProvider

object AttributionProvider:

	case class RoleDetails(role: UriResource, start: Option[Instant], end: Option[Instant], weight: Option[Int], extra: Option[String]){
		def isRelevantFor(dobj: DataObject): Boolean = dobj.specificInfo.fold(
			l3 => {
				val prodTime = l3.productionInfo.dateTime
				(start.map(s => s.compareTo(prodTime) < 0).getOrElse(true) &&
				end.map(e => e.compareTo(prodTime) > 0).getOrElse(true))
			},
			l2 => l2.acquisition.interval.fold(true){acqInt =>
				(start.map(s => s.compareTo(acqInt.stop) < 0).getOrElse(true) &&
				end.map(e => e.compareTo(acqInt.start) > 0).getOrElse(true))
			}
		)
	}

	case class Membership(person: Person, role: RoleDetails)
	case class PersonRole(org: UriResource, role: RoleDetails)

	given personOrdering: Ordering[Person] = Ordering
		.by[Person, String](_.lastName.toUpperCase)
		.orElseBy(_.firstName)

	given organizationOrdering: Ordering[Organization] = Ordering
		.by[Organization, String](_.name)

	given agentOrdering: Ordering[Agent] with
		def compare(a1: Agent, a2: Agent): Int = (a1, a2) match
			case (p1: Person,       p2: Person)       => personOrdering.compare(p1, p2)
			case (o1: Organization, o2: Organization) => organizationOrdering.compare(o1, o2)
			case ( _: Person,        _: Organization) => -1
			case ( _: Organization,  _: Person)       => 1

	given membershipOrdering: Ordering[Membership] = Ordering
		.by((m: Membership) => m.role.weight)
		.reverse
		.orElseBy(_.person)

	given personRoleOrdering: Ordering[PersonRole] = Ordering
		.by((pr: PersonRole) => pr.role.end.isEmpty)
		.orElseBy((pr: PersonRole) => pr.role.end)
		.orElseBy((pr: PersonRole) => pr.role.start.isDefined)
		.orElseBy((pr: PersonRole) => pr.role.start)
		.reverse

	def getOptLiteral(bs: BindingSet, name: String, litType: IRI): Option[Literal] = Option(bs.getValue(name)).collect{
		case lit: Literal if(lit.getDatatype == litType) => lit
	}

	def getOptInstant(bs: BindingSet, name: String): Option[Instant] = getOptLiteral(bs, name, XSD.DATETIME)
		.map(lit => Instant.parse(lit.stringValue))

	def getOptInt(bs: BindingSet, name: String): Option[Int] = getOptLiteral(bs, name, XSD.INTEGER)
		.map(lit => lit.intValue)

end AttributionProvider
