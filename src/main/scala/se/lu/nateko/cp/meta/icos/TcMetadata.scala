package se.lu.nateko.cp.meta.icos

import java.time.Instant

import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.{data => core}
import core.{Position, Orcid, Station}


sealed trait Entity[+T <: TC]{
	def cpId: UriId
	def tcIdOpt: Option[TcId[T]]
	def bestId: String = tcIdOpt.fold(cpId.urlSafeString)(_.id)
}

sealed trait TcEntity[+T <: TC] extends Entity[T]{
	def tcId: TcId[T]
	override def tcIdOpt = Some(tcId)
}

trait CpIdSwapper[E]{
	def withCpId(e: E, id: UriId): E
}

object Entity{

	implicit class IdSwapOps[E](e: E)(implicit swapper: CpIdSwapper[E]){
		def withCpId(id: UriId): E = swapper.withCpId(e, id)
	}

	implicit def persCpIdSwapper[T <: TC] = new CpIdSwapper[Person[T]]{
		def withCpId(p: Person[T], id: UriId) = p.copy(cpId = id)
	}

	implicit def orgCpIdSwapper[T <: TC] = new CpIdSwapper[Organization[T]]{
		def withCpId(org: Organization[T], id: UriId) = org match{
			case ss: TcStation[T] => ss.copy(cpId = id)
			case ci: CompanyOrInstitution[T] => ci.copy(cpId = id)
		}
	}

	implicit def instrCpIdSwapper[T <: TC] = new CpIdSwapper[Instrument[T]]{
		//noop, because instrument cpIds are expected to be stable
		def withCpId(instr: Instrument[T], id: UriId) = instr
	}

}

case class Person[+T <: TC](
	cpId: UriId,
	tcIdOpt: Option[TcId[T]],
	fname: String,
	lname: String,
	email: Option[String],
	orcid: Option[Orcid]
) extends Entity[T]

sealed trait Organization[+T <: TC] extends Entity[T]{ def name: String }

case class TcStation[+T <: TC](
	cpId: UriId,
	tcId: TcId[T],
	core: Station
) extends Organization[T] with TcEntity[T]{
	def name = core.org.name
}

case class CompanyOrInstitution[+T <: TC](cpId: UriId, tcIdOpt: Option[TcId[T]], name: String, label: Option[String]) extends Organization[T]

case class Instrument[+T <: TC : TcConf](
	tcId: TcId[T],
	model: String,
	sn: String,
	name: Option[String] = None,
	vendor: Option[Organization[T]] = None,
	owner: Option[Organization[T]] = None,
	partsCpIds: Seq[UriId] = Nil
) extends TcEntity[T]{
	//cpId for instruments is strictly related to tcId, and is expected to be stable
	def cpId = UriId(implicitly[TcConf[T]].tcPrefix + "_" + tcId.id)
}

class AssumedRole[+T <: TC](
	val kind: Role,
	val holder: Person[T],
	val org: Organization[T],
	val weight: Option[Int],
	val extra: Option[String]
){
	def id = (kind.name, holder.bestId, org.bestId)
	override def toString = s"AssumedRole($kind , $holder , $org )"
}

case class Membership[+T <: TC](cpId: UriId, role: AssumedRole[T], start: Option[Instant], stop: Option[Instant])

class TcState[+T <: TC : TcConf](val stations: Seq[TcStation[T]], val roles: Seq[Membership[T]], val instruments: Seq[Instrument[T]]){
	def tcConf = implicitly[TcConf[T]]
}

abstract class TcMetaSource[T <: TC : TcConf]{
	type State = TcState[T]
	def state: Source[State, Any]
	def stationId(baseId: String) = TcConf.stationId[T](baseId)
}
