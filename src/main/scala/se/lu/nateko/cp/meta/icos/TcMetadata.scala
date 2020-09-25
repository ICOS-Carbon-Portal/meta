package se.lu.nateko.cp.meta.icos

import java.time.Instant

import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.data.Orcid


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
			case ss: TcStationaryStation[T] => ss.copy(cpId = id)
			case ms: TcMobileStation[T] => ms.copy(cpId = id)
			case ci: CompanyOrInstitution[T] => ci.copy(cpId = id)
		}
	}

	implicit def instrCpIdSwapper[T <: TC] = new CpIdSwapper[Instrument[T]]{
		def withCpId(instr: Instrument[T], id: UriId) = instr.copy(cpId = id)
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

sealed trait TcStation[+T <: TC] extends Organization[T] with TcEntity[T]{
	def id: String
	def country: Option[CountryCode]
}

case class TcStationaryStation[+T <: TC](
	cpId: UriId,
	tcId: TcId[T],
	name: String,
	id: String,
	country: Option[CountryCode],
	pos: Position
) extends TcStation[T]

case class TcMobileStation[+T <: TC](
	cpId: UriId,
	tcId: TcId[T],
	name: String,
	id: String,
	country: Option[CountryCode],
	geoJson: Option[String]
) extends TcStation[T]

case class CompanyOrInstitution[+T <: TC](cpId: UriId, tcIdOpt: Option[TcId[T]], name: String, label: Option[String]) extends Organization[T]

case class Instrument[+T <: TC](
	cpId: UriId,
	tcId: TcId[T],
	model: String,
	sn: String,
	name: Option[String] = None,
	vendor: Option[Organization[T]] = None,
	owner: Option[Organization[T]] = None,
	partsCpIds: Seq[UriId] = Nil
) extends TcEntity[T]

class AssumedRole[+T <: TC](val kind: Role, val holder: Person[T], val org: Organization[T], val weight: Option[Int]){
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
