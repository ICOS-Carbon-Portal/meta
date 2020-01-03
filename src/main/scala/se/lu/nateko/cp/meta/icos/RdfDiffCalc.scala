package se.lu.nateko.cp.meta.icos

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement

import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import java.time.Instant
import se.lu.nateko.cp.meta.utils.Validated

class RdfDiffCalc(rdfMaker: RdfMaker, rdfReader: RdfReader) {

	import RdfDiffCalc._
	import SequenceDiff._

	def calcDiff[T <: TC : TcConf](newSnapshot: TcState[T]): Validated[Seq[RdfUpdate]] = for(
		current <- rdfReader.getCurrentState[T];
		cpOwnOrgs <- rdfReader.getCpOwnOrgs[T];
		cpOwnPeople <- rdfReader.getCpOwnPeople[T]
	) yield {

		def instrOrgs(instrs: Seq[Instrument[T]]) = instrs.map(_.owner).flatten ++ instrs.map(_.vendor).flatten

		val tcOrgs = instrOrgs(newSnapshot.instruments) ++ newSnapshot.roles.map(_.role.org) ++ newSnapshot.stations
		val cpOrgs = instrOrgs(current.instruments) ++ current.roles.map(_.role.org) ++ current.stations

		val orgsDiff = diff[T, Organization[T]](cpOrgs, tcOrgs, cpOwnOrgs)

		def updateInstr(instr: Instrument[T]): Instrument[T] = instr.copy(
			vendor = instr.vendor.map(orgsDiff.ensureIdPreservation),
			owner = instr.owner.map(orgsDiff.ensureIdPreservation)
		)

		val tcInstrs = newSnapshot.instruments.map(updateInstr)

		val instrDiff = diff[T, Instrument[T]](current.instruments, tcInstrs, Nil)

		val tcPeople = newSnapshot.roles.map(_.role.holder)
		val cpPeople = current.roles.map(_.role.holder)
		val peopleDiff = diff[T, Person[T]](cpPeople, tcPeople, cpOwnPeople)

		def updateRole(role: AssumedRole[T]) = new AssumedRole[T](
			role.kind,
			peopleDiff.ensureIdPreservation(role.holder),
			orgsDiff.ensureIdPreservation(role.org),
			role.weight
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
		val toAdd = toSet.diff(fromSet).toSeq.map(RdfUpdate(_, true))
		val toRemove = fromSet.diff(toSet).toSeq.map(RdfUpdate(_, false))
		toRemove ++ toAdd
	}

	private def swapSubject(subj: IRI)(stat: Statement): Statement =
		rdfMaker.createStatement(subj, stat.getPredicate, stat.getObject)

	private def swapObject(obj: IRI)(stat: Statement): Statement =
		rdfMaker.createStatement(stat.getSubject, stat.getPredicate, obj)


	def diff[T <: TC : TcConf, E <: Entity[T] : CpIdSwapper](from: Seq[E], to: Seq[E], cpOwn: Seq[E]): SequenceDiff[T, E] = {

		val Seq(fromMap, toMap, cpMap) = Seq(from, to, cpOwn).map(toTcIdMap[T, E])
		val Seq(fromKeys, toKeys, cpKeys) = Seq(fromMap, toMap, cpMap).map(_.keySet)

		val newOriginalAdded = {
			val newEnts = toKeys.diff(fromKeys).diff(cpKeys).toSeq.map(toMap.apply)
			val initCpIds = cpOwn.map(_.cpId).toSet

			val (_, dedupedEnts) = newEnts.foldLeft[(Set[String], List[E])]((initCpIds, Nil)){
				case ((cpIds, ents), ent) =>
					val idAttempts = Iterator(ent.cpId) ++ Iterator.from(2).map{i =>s"${ent.cpId}_$i"}
					val newCpId = idAttempts.find(!cpIds.contains(_)).get //will always find something
					(cpIds + newCpId) -> (ent.withCpId(newCpId) :: ents)
			}

			val rdfDiff = dedupedEnts.flatMap(rdfMaker.getStatements[T]).map(RdfUpdate(_, true))

			val map = dedupedEnts.flatMap(ent => ent.tcIdOpt.map(_ -> ent.cpId)).filter{
				case (key, cpId) => toMap(key).cpId != cpId
			}.toMap

			new SequenceDiff[T, E](rdfDiff, map)
		}

		val existingChangedTcOnly = toKeys.intersect(fromKeys).diff(cpKeys).toSeq.map{key =>

			val (from, to) = (fromMap(key), toMap(key))

			val rdfDiff = statsDiff(
				rdfMaker.getStatements[T](from),
				rdfMaker.getStatements[T](to.withCpId(from.cpId))
			)

			val map: Map[TcId[T], String] =
				if(from.cpId == to.cpId)
					Map.empty
				else
					Map(key -> from.cpId)

			new SequenceDiff[T, E](rdfDiff, map)
		}.join

		val oldOriginalRemoved = SequenceDiff.empty[T, E] //no entities are deleted

		val newButPresentInCp = {
			val map = toKeys.diff(fromKeys).intersect(cpKeys).toSeq.collect{
				case key if toMap(key).cpId != cpMap(key).cpId => key -> cpMap(key).cpId
			}.toMap
			new SequenceDiff[T, E](Nil, map)
		}

		val existingAppearedInCp = {
			val keys = cpKeys.intersect(fromKeys).toSeq
keys.map(_.id).foreach(println)
//TODO Debug this code segment
			val rdfDiff = keys.flatMap{key =>

				val toDelete = fromMap(key)
				val deletedIri = rdfMaker.getIri(toDelete)
				val replacementIri = rdfMaker.getIri(cpMap(key))

				val basicEntityStatements = rdfMaker.getStatements(toDelete).toSet

				val (redundantBasicStatements, extraStatements) = rdfReader.getTcOnlyStatements(deletedIri)
					.partition(basicEntityStatements.contains)

				val redundantExtraStatements = if(deletedIri != replacementIri) extraStatements else Nil
				val replacementExtraStatements = redundantExtraStatements.map(swapSubject(replacementIri))

				val usagesToRemove = if(deletedIri != replacementIri)
						rdfReader.getTcOnlyUsages(deletedIri)
					else Nil

				val replacementUsages = usagesToRemove.map(swapObject(replacementIri))

				(redundantBasicStatements ++ redundantExtraStatements ++ usagesToRemove).map(RdfUpdate(_, false)) ++
				(replacementExtraStatements ++ replacementUsages).map(RdfUpdate(_, true))
			}
rdfDiff.foreach(println)
			val lookup = keys.collect{
				case key if fromMap(key).cpId != cpMap(key).cpId => key -> cpMap(key).cpId
			}.toMap
			new SequenceDiff[T, E](rdfDiff, lookup)
		}

		Seq(newOriginalAdded, oldOriginalRemoved, existingChangedTcOnly, newButPresentInCp, existingAppearedInCp).join
	}

	def rolesDiff[T <: TC : TcConf](cp: Seq[Membership[T]], tc: Seq[Membership[T]]): Seq[RdfUpdate] = {
		val cpMap = cp.groupBy(m => m.role.id)
		val tcMap = tc.groupBy(m => m.role.id)
		val finishedIds = cpMap.keySet.diff(tcMap.keySet).toSeq
		val newIds = tcMap.keySet.diff(cpMap.keySet).toSeq

		val newStart: Option[Instant] = if(cp.isEmpty) None else Some(Instant.now)

		val newMembs = newIds.flatMap(tcMap.apply).flatMap{memb =>
			val membId = scala.util.Random.alphanumeric.take(24).mkString
			val theStart = memb.start.orElse(if(memb.stop.isEmpty) newStart else None)
			val newMemb = memb.copy(cpId = membId, start = theStart)
			rdfMaker.getStatements[T](newMemb).map(RdfUpdate(_, true))
		}

		val endedMembs = finishedIds.flatMap(cpMap.apply).filter(_.stop.isEmpty).map{memb =>
			val stat = rdfMaker.getMembershipEnd(memb.cpId)
			RdfUpdate(stat, true)
		}
		//TODO Add handling of intersecting roles (could mean a noop or, in some cases, retraction of stop times)
		//TODO Handle the case of person holding the same role at the same org more than once, at non-overlapping time intervals
		//TODO Do not end memberships (or delete entities) that have disappeared from TC's metadata. TCs must provide end dates explicitly.
		//TODO Handle duplicates (in the sense of identical cpId but different tcId)
		//TODO Handle conflicting statements from CP and TC (e.g. different emails or name spellings)
		newMembs ++ endedMembs
	}
}

object RdfDiffCalc{
	def toTcIdMap[T <: TC, E <: Entity[T]](ents: Seq[E]): Map[TcId[T], E] = ents.flatMap(e => e.tcIdOpt.map(_ -> e)).toMap
}

class SequenceDiff[T <: TC, E <: Entity[T] : CpIdSwapper](val rdfDiff: Seq[RdfUpdate], private val cpIdLookup: Map[TcId[T], String]){

	def ensureIdPreservation(entity: E): E = entity.tcIdOpt.flatMap(cpIdLookup.get).fold(entity)(entity.withCpId)
}

object SequenceDiff{

	def empty[T <: TC, E <: Entity[T] : CpIdSwapper] = new SequenceDiff[T, E](Nil, Map.empty)

	implicit class SeqDiffSeqEnriched[T <: TC, E <: Entity[T] : CpIdSwapper](val seq: Seq[SequenceDiff[T, E]]) {
		def join: SequenceDiff[T, E] = {
			val rdfDiff = seq.flatMap(_.rdfDiff)
			val lookup = Map(seq.flatMap(_.cpIdLookup): _*)
			new SequenceDiff(rdfDiff, lookup)
		}
	}

	implicit class RdfUpdSeqEnriched(val rdfupd: Seq[RdfUpdate]) extends AnyVal{
		def toSeqDiff[T <: TC, E <: Entity[T] : CpIdSwapper] = new SequenceDiff[T, E](rdfupd, Map.empty)
	}
}
