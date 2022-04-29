package se.lu.nateko.cp.meta.icos

import scala.collection.mutable.Set
import se.lu.nateko.cp.meta.api.UriId

object RolesDiffCalc{

	def resultingMembsForSameAssumedRole[T <: TC](current: Seq[Membership[T]], latest: Seq[Membership[T]]): Seq[Membership[T]] = {
		if(latest.size == 1)
			current.headOption.toSeq.map(curHead => latest.head.copy(cpId = curHead.cpId))

		else { //the rest are rare corner cases for when person occupied same role (at same org) intermittently
			val remainingLatest = Set(latest*)

			current.flatMap{c =>
				if(remainingLatest.isEmpty) None else Some(remainingLatest.maxBy(l => similarity(c, l))).map{res =>
					remainingLatest -= res
					c.cpId -> res
				}
			}.groupBy(_._2).map(_._2.head).toSeq.map{
				case (cpId, m) => m.copy(cpId = cpId)
			} ++
			remainingLatest.toSeq.map(_.copy(cpId = newMembId))
		}
	}

	private def similarity[T <: TC](current: Membership[T], latest: Membership[T]): Int = {
		var res: Int = 0
		if(current.start == latest.start){
			res += (if(current.start.isEmpty) 10 else 100)
		}
		if(current.stop == latest.stop){
			res += (if(current.stop.isEmpty) 10 else 100)
		}
		if(current.start.isEmpty && latest.start.isDefined) res += 10
		if(current.stop.isEmpty && latest.stop.isDefined) res += 10
		if(latest.start.isEmpty && current.start.isDefined) res -= 10
		if(latest.stop.isEmpty && current.stop.isDefined) res -= 10
		res
	}


	def newMembId = UriId(scala.util.Random.alphanumeric.take(24).mkString)
}
