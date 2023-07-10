package se.lu.nateko.cp.meta.icos

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value

import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import java.time.Instant
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.===
import org.eclipse.rdf4j.model.Resource
import scala.collection.mutable.Buffer
import org.eclipse.rdf4j.model.ValueFactory

class RdfDiffCalc(rdfMaker: RdfMaker, rdfReader: RdfReader) {

	import RdfDiffCalc.*
	import SequenceDiff.*
	private val multivaluePredicates = Set(rdfMaker.meta.hasMembership)

	def calcDiff[T <: TC : TcConf](newSnapshot: TcState[T]): Validated[Seq[RdfUpdate]] = for(
		current <- rdfReader.getCurrentState[T].require("problem reading current state");
		cpOwnOrgs <- rdfReader.getCpOwnOrgs[T].require("problem reading CP own orgs");
		cpOwnPeople <- rdfReader.getCpOwnPeople[T].require("problem reading CP own people")
	) yield {

		def plainOrgs(s: TcState[T]): Seq[TcPlainOrg[T]] = uniqBestId(
			(
				s.instruments.flatMap(_.owner) ++ s.instruments.flatMap(_.vendor) ++
				s.roles.map(_.role.org) ++ s.stations.flatMap{st =>
					st.funding.map(_.funder) ++ st.responsibleOrg
				}
			).collect{
				case o: TcPlainOrg[T] => o
			}
		)

		val plainOrgsDiff = diff[T, TcPlainOrg[T]](plainOrgs(current), plainOrgs(newSnapshot), cpOwnOrgs)

		def updateStation(st: TcStation[T]): TcStation[T] = st.copy(
			responsibleOrg = st.responsibleOrg.map(o => plainOrgsDiff.ensureIdPreservation(o)),
			funding = st.funding.map(updateFunding)
		)

		def updateFunding(f: TcFunding[T]): TcFunding[T] =
			plainOrgsDiff.ensureIdPreservation(f.funder) match {
				case updFunder: TcFunder[T] =>
					f.copy(
						funder = updFunder,
						core = f.core.copy(funder = updFunder.core)
					)
				case null => f
			}

		val stationsDiff = diff[T, TcStation[T]](current.stations, newSnapshot.stations.map(updateStation), Nil)

		val orgsDiff: SequenceDiff[T] = plainOrgsDiff.concat(stationsDiff)

		def updateInstr(instr: TcInstrument[T]): TcInstrument[T] = instr.copy(
			vendor = instr.vendor.map(v => orgsDiff.ensureIdPreservation(v)),
			owner = instr.owner.map(o => orgsDiff.ensureIdPreservation(o)),
			deployments = instr.deployments.map(d => orgsDiff.ensureIdPreservation(d))
		)

		val tcInstrs = newSnapshot.instruments.map(updateInstr)

		val instrDiff = diff[T, TcInstrument[T]](current.instruments, tcInstrs, Nil)

		val tcPeople = uniqBestId(newSnapshot.roles.map(_.role.holder))
		val cpPeople = uniqBestId(current.roles.map(_.role.holder))
		val peopleDiff = diff[T, TcPerson[T]](cpPeople, tcPeople, cpOwnPeople)

		def updateRole(role: AssumedRole[T]) = new AssumedRole[T](
			role.kind,
			peopleDiff.ensureIdPreservation(role.holder),
			orgsDiff.ensureIdPreservation(role.org),
			role.weight,
			role.extra
		)

		val tcRoles = newSnapshot.roles.map(memb => memb.copy(role = updateRole(memb.role)))

		val rolesRdfDiff = rolesDiff[T](current.roles, tcRoles)

		rdfReader.keepMeaningful(
			orgsDiff.rdfDiff ++ instrDiff.rdfDiff ++ peopleDiff.rdfDiff ++ rolesRdfDiff
		)
	}

	private def statsDiff(from: Seq[Statement], to: Seq[Statement]): Seq[RdfUpdate] = {
		val fromSet = from.toSet
		val toSet = to.toSet
		val assertions = toSet.diff(fromSet).toSeq.map(RdfUpdate(_, true))
		val mentionedPredicates = toSet.map(_.getPredicate)
		val retractions = fromSet.diff(toSet).iterator.collect{
			//all the predicates are made "sticky", meaning one cannot remove a value, but can update
			case st if mentionedPredicates.contains(st.getPredicate) => RdfUpdate(st, false)
		}.toSeq
		retractions ++ assertions
	}

	private def swapSubject(subj: IRI)(stat: Statement): Statement =
		rdfMaker.createStatement(subj, stat.getPredicate, stat.getObject)

	private def swapObject(obj: IRI)(stat: Statement): Statement =
		rdfMaker.createStatement(stat.getSubject, stat.getPredicate, obj)


	def diff[T <: TC : TcConf, E <: Entity[T] : CpIdSwapper](from: Seq[E], to: Seq[E], cpOwn: Seq[E]): SequenceDiff[T] = {

		val Seq(fromMap, toMap, cpMap) = Seq(from, to, cpOwn).map(toTcIdMap[T, E])
		val Seq(fromKeys, toKeys, cpKeys) = Seq(fromMap, toMap, cpMap).map(_.keySet)

		def cpIdLookup(keys: Iterable[TcId[T]], map: Map[TcId[T], E]): Map[TcId[T], UriId] = keys.collect{
			case key if map(key).cpId != cpMap(key).cpId => key -> cpMap(key).cpId
		}.toMap

		val newOriginalAdded = {
			val newEnts = toKeys.diff(fromKeys).diff(cpKeys).toSeq.map(toMap.apply)
			val initCpIds: Set[UriId] = (from ++ cpOwn).map(_.cpId).toSet

			val (_, dedupedEnts) = newEnts.foldLeft[(Set[UriId], List[E])]((initCpIds, Nil)){
				case ((cpIds, ents), ent) =>
					val idAttempts = Iterator(ent.cpId) ++ Iterator.from(2).map{i =>UriId(s"${ent.cpId}_$i")}
					val newCpId = idAttempts.find(!cpIds.contains(_)).get //will always find something
					(cpIds + newCpId) -> (ent.withCpId(newCpId) :: ents)
			}

			val rdfDiff = dedupedEnts.flatMap(rdfMaker.getStatements[T]).map(RdfUpdate(_, true))

			val idLookup = dedupedEnts.flatMap(ent => ent.tcIdOpt.map(_ -> ent.cpId)).filter{
				case (key, cpId) => toMap(key).cpId != cpId
			}.toMap

			new SequenceDiff(rdfDiff, idLookup)
		}

		val existingChangedTcOnly = toKeys.intersect(fromKeys).diff(cpKeys).toSeq.map{key =>

			val (from, to) = (fromMap(key), toMap(key))

			val rdfDiff = statsDiff(
				rdfMaker.getStatements[T](from),
				rdfMaker.getStatements[T](to.withCpId(from.cpId))
			)

			val idLookup: Map[TcId[T], UriId] =
				if(from.cpId == to.cpId)
					Map.empty
				else
					Map(key -> from.cpId)

			new SequenceDiff(rdfDiff, idLookup)
		}.join

		//no entities are deleted, cpIdLookup covered in 'existingAppearedInCp'
		val oldOriginalRemoved = SequenceDiff.empty[T]

		val presentInCp = { //needed for completeness of cpIdLookup info
			val idLookup = cpIdLookup(toKeys.intersect(cpKeys), toMap)
			new SequenceDiff(Nil, idLookup)
		}

		val existingAppearedInCp = {
			val keys = cpKeys.intersect(fromKeys).intersect(toKeys).toSeq

			val rdfDiff = keys.flatMap{key =>

				val deleteCands = from.filter(e => e.tcIdOpt.contains(key))
				val deleteCand: Option[E] = if(deleteCands.size > 1)
						deleteCands.find(e => e.cpId != cpMap(key).cpId) //should delete TC's that has different URI than CP's
					else deleteCands.headOption
				val toDelete = deleteCand.getOrElse(throw new Exception("Algorithmic error in RdfDiffCalc"))

				val deletedIri = rdfMaker.getIri(toDelete)
				val replacementIri = rdfMaker.getIri(cpMap(key))

				val cpPredicates = rdfReader.getCpStatements(replacementIri).map(_.getPredicate)
					.filterNot(multivaluePredicates.contains).toSet //multiple-value properties are possible

				val (statementsToDelete, statementsToKeep) = rdfReader.getTcOnlyStatements(deletedIri)
					.partition(st => cpPredicates.contains(st.getPredicate)) //CP statements override TCs'

				val renamingUpdates = if(deletedIri === replacementIri) Nil else {
					statementsToKeep.map(RdfUpdate(_, false)) ++ statementsToKeep.map(swapSubject(replacementIri)).map(RdfUpdate(_, true))
				}

				val renamingUsages = if(deletedIri === replacementIri) Nil else {
					val usagesToRename = rdfReader.getTcOnlyUsages(deletedIri)
					usagesToRename.map(RdfUpdate(_, false)) ++ usagesToRename.map(swapObject(replacementIri)).map(RdfUpdate(_, true))
				}

				renamingUpdates ++ renamingUsages ++ statementsToDelete.map(RdfUpdate(_, false))
			}

			new SequenceDiff(rdfDiff, cpIdLookup(keys, fromMap))
		}

		Seq(newOriginalAdded, oldOriginalRemoved, existingChangedTcOnly, presentInCp, existingAppearedInCp).join
	}

	def rolesDiff[T <: TC](cp: Seq[Membership[T]], tc: Seq[Membership[T]]): Seq[RdfUpdate] = {
		val cpMap = cp.groupBy(m => m.role.id)
		val tcMap = tc.groupBy(m => m.role.id)
		val newIds = tcMap.keySet.diff(cpMap.keySet).toSeq

		val newMembs = newIds.flatMap(tcMap.apply).flatMap{memb =>
			val newMemb = memb.copy(cpId = RolesDiffCalc.newMembId)
			rdfMaker.getStatements[T](newMemb).map(RdfUpdate(_, true))
		}

		val updatedMembs = cpMap.keySet.intersect(tcMap.keySet).toSeq.flatMap{key =>
			val currentMembs = cpMap(key)
			val latestMembs = tcMap(key)
			val newMembs = RolesDiffCalc.resultingMembsForSameAssumedRole(currentMembs, latestMembs)
			statsDiff(
				currentMembs.flatMap(rdfMaker.getStatements[T]),
				newMembs.flatMap(rdfMaker.getStatements[T])
			)
		}
		newMembs ++ updatedMembs
	}
}

object RdfDiffCalc{

	def toTcIdMap[T <: TC, E <: Entity[T]](ents: Seq[E]): Map[TcId[T], E] = ents.flatMap(e => e.tcIdOpt.map(_ -> e)).toMap

	def uniqBestId[E <: Entity[_]](ents: Seq[E]): Seq[E] = ents.groupBy(_.bestId).map(_._2.head).toSeq
}

class SequenceDiff[T <: TC](val rdfDiff: Seq[RdfUpdate], private val cpIdLookup: Map[TcId[T], UriId]){

	def ensureIdPreservation[E <: Entity[T] : CpIdSwapper](entity: E): E =
		entity.tcIdOpt.flatMap(cpIdLookup.get).fold(entity)(entity.withCpId)

	def concat(other: SequenceDiff[T]) = new SequenceDiff[T](
		rdfDiff ++ other.rdfDiff,
		cpIdLookup ++ other.cpIdLookup
	)
}

object SequenceDiff{

	def empty[T <: TC] = new SequenceDiff[T](Nil, Map.empty)

	extension [T <: TC](seq: Seq[SequenceDiff[T]])
		def join: SequenceDiff[T] = {
			val rdfDiff = seq.flatMap(_.rdfDiff)
			val lookup = Map(seq.flatMap(_.cpIdLookup)*)
			new SequenceDiff(rdfDiff, lookup)
		}


	extension (rdfupd: Seq[RdfUpdate])
		def toSeqDiff[T <: TC] = new SequenceDiff[T](rdfupd, Map.empty)

}

class RdfDiffBuilder(factory: ValueFactory):
	import RdfDiffBuilder.*
	import scala.collection.mutable

	private val allDiffs = mutable.Map.empty[SubjPred, Buffer[Diff]]

	def update(stats: Seq[Statement], typ: UpdateType): Unit = stats.foreach: stat =>
		val diffs = allDiffs.getOrElseUpdate(stat.getSubject -> stat.getPredicate, Buffer.empty[Diff])
		diffs += stat.getObject -> typ

	def build: Seq[RdfUpdate] =
		val updates = for
			((subj, pred), diffs) <- allDiffs
			(obj, updType) <- materialize(diffs.toIndexedSeq)
		yield
			val isAssertion = updType match
				case Assertion => true
				case Retraction => false
			val stat = factory.createStatement(subj, pred, obj)
			RdfUpdate(stat, isAssertion)
		updates.toSeq
	

object RdfDiffBuilder:
	case object Assertion
	case object Retraction
	case object WeakRetraction //only retract if there is a later replacement assertion

	type ProperUpdateType = Assertion.type | Retraction.type
	type UpdateType = ProperUpdateType | WeakRetraction.type
	type SubjPred = (Resource, IRI)
	type Diff = (Value, UpdateType)
	type ProperDiff = (Value, ProperUpdateType)

	def materialize(diffs: IndexedSeq[Diff]): IndexedSeq[ProperDiff] =
		val firstPass: IndexedSeq[ProperDiff] = diffs.indices.flatMap: i =>
			diffs.apply(i) match
				case (v, WeakRetraction) =>
					if diffs.drop(i + 1).collectFirst{case (_, Assertion) => true}.isDefined //has a later assertion
					then Some(v -> Retraction) else None
				case (v, pUpd: ProperUpdateType) => Some(v -> pUpd)

		if firstPass.length <= 1 then firstPass else firstPass
			.groupMapReduce[Value, Option[ProperUpdateType]](_._1)(tpl => Some(tpl._2)):
				case (None, x) => x
				case (x, None) => x
				case (Some(Assertion),  Some(Retraction)) => None
				case (Some(Retraction), Some(Assertion))  => Some(Assertion)
				case (Some(Assertion),  Some(Assertion))  => Some(Assertion)
				case (Some(Retraction), Some(Retraction)) => Some(Retraction)
			.collect:
				case (v, Some(pUpd)) => v -> pUpd
			.toIndexedSeq
