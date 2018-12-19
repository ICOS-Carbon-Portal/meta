package se.lu.nateko.cp.meta.icos

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value

import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

class RdfDiffCalc(rdfMaker: RdfMaker, rdfReader: RdfReader) {

	import RdfDiffCalc._

	def calcDiff[T <: TC](newSnapshot: TcState[T]): Seq[RdfUpdate] = {

		val current: CpTcState[T] = rdfReader.getCurrentState[T]

		def instrOrgs(instrs: Seq[Instrument[T]]) = instrs.map(_.owner).flatten ++ instrs.map(_.vendor).flatten

		val tcOrgs = instrOrgs(newSnapshot.instruments) ++ newSnapshot.roles.map(_.org)
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
			.flatMap(rdfMaker.getStatements[T]).map(RdfUpdate(_, true))

		val oldOriginalRemoved = Nil //no entities are deleted

		//TODO Complete the RdfUpdate seq and implement the map
		new SequenceDiff(newOriginalAdded ++ oldOriginalRemoved, Map.empty)
	}

}

object RdfDiffCalc{
	def toTcIdMap[T <: TC, E <: Entity[T]](ents: Seq[E]): Map[TcId[T], E] = ents.map(e => e.tcId -> e).toMap
}

class SequenceDiff[T <: TC, E <: Entity[T]](val rdfDiff: Seq[RdfUpdate], cpIdLookup: Map[TcId[T], String]){

	def ensureIdPreservation(entity: E): E = cpIdLookup.get(entity.tcId) match {
		case None => entity
		case Some(cpId) => entity.withCpId(cpId)
	}
}
