package se.lu.nateko.cp.meta.services.attribution

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Value, ValueFactory}
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.rio.helpers.NTriplesUtil
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlRunner}
import se.lu.nateko.cp.meta.api.RdfLens.MetaConn
import se.lu.nateko.cp.meta.core.data.{Agent, DataObject, Organization, Person, UriResource}
import se.lu.nateko.cp.meta.instanceserver.{RdfStatement, StatementSource}
import se.lu.nateko.cp.meta.services.upload.CpmetaReader
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import java.time.Instant

final class AttributionProvider(vocab: CpVocab, val metaVocab: CpmetaVocab) extends CpmetaReader:
	import AttributionProvider.*
	import StatementSource.*
	given ValueFactory = vocab.factory

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

	def getMemberships(org: URI)(using MetaConn): Validated[IndexedSeq[Membership]] =
		readMemberships(org)

	/** Complete membership model for a landing page, with no remote reads during parsing. */
	def getMembershipsBatched(org: URI)(using conn: MetaConn, sparql: SparqlRunner): Validated[IndexedSeq[Membership]] =
		Validated:
			val result = sparql.evaluateGraphQuery(membershipsQuery(org, conn.readContexts))
			try result.map(RdfStatement.fromRdf4jStatement).toIndexedSeq.distinct
			finally result.close()
		.flatMap: statements =>
			given StatementSource = new StatementSource:
				def getStatements(s: IRI | Null, p: IRI | Null, o: Value | Null): CloseableIterator[RdfStatement] =
					new CloseableIterator.Wrap(statements.iterator.filter(st =>
						(s == null || st.subject == s) && (p == null || st.predicate == p) && (o == null || st.obj == o)
					), () => ())
				def hasStatement(s: IRI | Null, p: IRI | Null, o: Value | Null): Boolean =
					val iter = getStatements(s, p, o)
					try iter.hasNext finally iter.close()
			readMemberships(org)

	private[attribution] def membershipsQuery(org: URI, contexts: Seq[IRI]): String =
		def term(iri: IRI): String = NTriplesUtil.toNTriplesString(iri)
		def props(iris: IRI*): String = iris.map(term).mkString(" ")
		import metaVocab.*
		val from = contexts.distinct.map(c => s"FROM ${term(c)}").mkString("\n")
		val atOrg = term(metaVocab.atOrganization)
		val membership = term(metaVocab.hasMembership)
		val role = term(metaVocab.hasRole)
		s"""CONSTRUCT {
			| ?m $atOrg ${term(org.toRdf)} .
			| ?person $membership ?m .
			| ?s ?p ?o .
			|}
			|$from
			|WHERE {
			| ?m $atOrg ${term(org.toRdf)} .
			| ?person $membership ?m .
			| FILTER(isIRI(?m) && isIRI(?person))
			| OPTIONAL {
			|   { VALUES ?p { ${props(hasRole, hasStartTime, hasEndTime, hasAttributionWeight, hasExtraRoleInfo)} }
			|     ?m ?p ?o . BIND(?m AS ?s) }
			|   UNION { VALUES ?p { ${props(RDFS.LABEL, RDFS.COMMENT, hasFirstName, hasLastName, hasEmail, hasOrcidId)} }
			|     ?person ?p ?o . BIND(?person AS ?s) }
			|   UNION { VALUES ?p { ${props(RDFS.LABEL, RDFS.COMMENT)} }
			|     ?m $role ?s . FILTER(isIRI(?s)) ?s ?p ?o }
			| }
			|}""".stripMargin

	private def readMemberships(org: URI)(using StatementSource): Validated[IndexedSeq[Membership]] =
		Validated.sequence:
			for
				memb <- getPropValueHolders(metaVocab.atOrganization, org.toRdf)
				person <- getPropValueHolders(metaVocab.hasMembership, memb)
			yield for
				person <- getPerson(person)
				role <- readRoleDetails(memb)
			yield Membership(person, role)


	def getPersonRoles(person: URI)(using MetaConn): Validated[IndexedSeq[PersonRole]] =
		Validated.sequence:
			getUriValues(person.toRdf,  metaVocab.hasMembership).map: memb =>
				for
					org <- getLabeledResource(memb, metaVocab.atOrganization)
					role <- readRoleDetails(memb)
				yield PersonRole(org, role)


	private def readRoleDetails(memb: IRI)(using StatementSource): Validated[RoleDetails] =
		for
			role <- getLabeledResource(memb, metaVocab.hasRole)
			start <- getOptionalInstant(memb, metaVocab.hasStartTime)
			stop <- getOptionalInstant(memb, metaVocab.hasEndTime)
			weight <- getOptionalInt(memb, metaVocab.hasAttributionWeight)
			extra <- getOptionalString(memb, metaVocab.hasExtraRoleInfo)
		yield
			RoleDetails(role, start, stop, weight, extra)

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

/**
 * `RoleDetails`, `Membership` and `PersonRole` are only ever *named* by meta
 * (`api/OrganizationExtra.scala`), but they cannot move there: all three are in the public
 * signature of the shared `AttributionProvider` above (`getMemberships`, `getPersonRoles`) and
 * `RoleDetails`/`Membership` drive its internal filtering and orderings. rdfStore reaches the
 * same class through `CitationMaker`.
 */
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

end AttributionProvider
