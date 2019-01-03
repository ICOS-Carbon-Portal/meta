package se.lu.nateko.cp.meta.icos

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value

import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

class RdfDiffCalc(rdfMaker: RdfMaker, rdfReader: RdfReader) {

	import RdfDiffCalc._
	import SequenceDiff._

	def calcDiff[T <: TC](newSnapshot: TcState[T]): Seq[RdfUpdate] = {

		val current: CpTcState[T] = rdfReader.getCurrentState[T]

		def instrOrgs(instrs: Seq[Instrument[T]]) = instrs.map(_.owner).flatten ++ instrs.map(_.vendor).flatten

		val tcOrgs = instrOrgs(newSnapshot.instruments) ++ newSnapshot.roles.map(_.org) ++ newSnapshot.stations
		val cpOrgs = instrOrgs(current.instruments) ++ current.roles.map(_.role.org)
		val cpOwnOrgs = rdfReader.getCpOwnOrgs[T]

		val orgsDiff = diff[T, Organization[T]](cpOrgs, tcOrgs, cpOwnOrgs)

		def updateInstr(instr: Instrument[T]): Instrument[T] = instr.copy(
			vendor = instr.vendor.map(orgsDiff.ensureIdPreservation),
			owner = instr.owner.map(orgsDiff.ensureIdPreservation),
			parts = instr.parts.map(updateInstr)
		)

		val tcInstrs = newSnapshot.instruments.map(updateInstr)
		val cpInstrs = current.instruments.map(updateInstr)

		val instrDiff = diff[T, Instrument[T]](cpInstrs, tcInstrs, Nil)

		orgsDiff.rdfDiff ++ instrDiff.rdfDiff
	}

	def diff(from: Seq[Statement], to: Seq[Statement]): Seq[RdfUpdate] = {
		val fromSet = from.toSet
		val toSet = to.toSet
		val toAdd = toSet.diff(fromSet).toSeq.map(RdfUpdate(_, true))
		val toRemove = fromSet.diff(toSet).toSeq.map(RdfUpdate(_, false))
		toRemove ++ toAdd
	}

	def diff[T <: TC, E <: Entity[T]](from: Seq[E], to: Seq[E], cpOwn: Seq[E]): SequenceDiff[T, E] = {

		val Seq(fromMap, toMap, cpMap) = Seq(from, to, cpOwn).map(toTcIdMap[T, E])
		val Seq(fromKeys, toKeys, cpKeys) = Seq(fromMap, toMap, cpMap).map(_.keySet)

		val newOriginalAdded = toKeys.diff(fromKeys).diff(cpKeys).toSeq.map(toMap.apply)
			.flatMap(rdfMaker.getStatements[T]).map(RdfUpdate(_, true)).toSeqDiff[T, E]

		val existingChangedTcOnly = toKeys.intersect(fromKeys).diff(cpKeys).toSeq.map{key =>

			val (from, to) = (fromMap(key), toMap(key))

			val rdfDiff = diff(
				rdfMaker.getStatements(from),
				rdfMaker.getStatements(to.withCpId(from.cpId))
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
			val rdfDiff = keys.flatMap(key => rdfMaker.getStatements(fromMap(key)).map(RdfUpdate(_, false)))
			val lookup = keys.collect{
				case key if fromMap(key).cpId != cpMap(key).cpId => key -> cpMap(key).cpId
			}.toMap
			new SequenceDiff[T, E](rdfDiff, lookup)
		}

		Seq(newOriginalAdded, oldOriginalRemoved, existingChangedTcOnly, newButPresentInCp, existingAppearedInCp).join
	}

}

object RdfDiffCalc{
	def toTcIdMap[T <: TC, E <: Entity[T]](ents: Seq[E]): Map[TcId[T], E] = ents.map(e => e.tcId -> e).toMap
}

class SequenceDiff[T <: TC, E <: Entity[T]](val rdfDiff: Seq[RdfUpdate], private val cpIdLookup: Map[TcId[T], String]){

	def ensureIdPreservation(entity: E): E = cpIdLookup.get(entity.tcId) match {
		case None => entity
		case Some(cpId) => entity.withCpId(cpId)
	}
}

object SequenceDiff{

	def empty[T <: TC, E <: Entity[T]] = new SequenceDiff[T, E](Nil, Map.empty)

	implicit class SeqDiffSeqEnriched[T <: TC, E <: Entity[T]](val seq: Seq[SequenceDiff[T, E]]) extends AnyVal {
		def join: SequenceDiff[T, E] = {
			val rdfDiff = seq.flatMap(_.rdfDiff)
			val lookup = Map(seq.flatMap(_.cpIdLookup): _*)
			new SequenceDiff(rdfDiff, lookup)
		}
	}

	implicit class RdfUpdSeqEnriched(val rdfupd: Seq[RdfUpdate]) extends AnyVal{
		def toSeqDiff[T <: TC, E <: Entity[T]] = new SequenceDiff[T, E](rdfupd, Map.empty)
	}
}
