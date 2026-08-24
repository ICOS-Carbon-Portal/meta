package se.lu.nateko.cp.meta.services.attribution

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import se.lu.nateko.cp.meta.api.RdfLens.MetaConn
import se.lu.nateko.cp.meta.core.data.{Agent, DataObject, Organization, Person, UriResource}
import se.lu.nateko.cp.meta.instanceserver.StatementSource
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
		l2 =>
			val tcFilter = getTcSpecificFilter(dobj)
			getMemberships(l2.acquisition.station.org.self.uri, role => tcFilter(role) && role.isRelevantFor(dobj)).map(
				_.sorted
				.map(_.person)
				.distinct
			)
	)

	def getMemberships(org: URI)(using MetaConn): Validated[IndexedSeq[Membership]] =
		getMemberships(org, _ => true)

	/** The memberships at the organization, with the people read only for the roles that are kept */
	private def getMemberships(org: URI, keepRole: RoleDetails => Boolean)(using MetaConn): Validated[IndexedSeq[Membership]] =
		Validated.sequence:
			getPropValueHolders(metaVocab.atOrganization, org.toRdf)
				// instrument deployments are at organizations, too
				.filter(resourceHasType(_, metaVocab.membershipClass))
				.map: memb =>
					readRoleDetails(memb).flatMap: role =>
						if !keepRole(role) then Validated.ok(Nil)
						else Validated.sequence:
							getPropValueHolders(metaVocab.hasMembership, memb).map: person =>
								getPerson(person).map(Membership(_, role))
		.map(_.flatten)


	def getPersonRoles(person: URI)(using MetaConn): Validated[IndexedSeq[PersonRole]] =
		Validated.sequence:
			getUriValues(person.toRdf,  metaVocab.hasMembership).map: memb =>
				for
					org <- getLabeledResource(memb, metaVocab.atOrganization)
					role <- readRoleDetails(memb)
				yield PersonRole(org, role)


	private def readRoleDetails(memb: IRI)(using MetaConn): Validated[RoleDetails] =
		for
			role <- getLabeledResource(memb, metaVocab.hasRole)
			start <- getOptionalInstant(memb, metaVocab.hasStartTime)
			stop <- getOptionalInstant(memb, metaVocab.hasEndTime)
			weight <- getOptionalInt(memb, metaVocab.hasAttributionWeight)
			extra <- getOptionalString(memb, metaVocab.hasExtraRoleInfo)
		yield
			RoleDetails(role, start, stop, weight, extra)

	private def getTcSpecificFilter(dobj: DataObject): RoleDetails => Boolean =
		if(dobj.specification.theme.self.uri === vocab.atmoTheme) role => (role.weight.isDefined && {
			val speciesOk = for(
				extra <- role.extra;
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
