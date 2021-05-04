package se.lu.nateko.cp.meta.icos

import java.time.Instant

import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.{Position, Orcid, Station, Organization}
import se.lu.nateko.cp.meta.services.CpVocab


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

	implicit def persCpIdSwapper[T <: TC] = new CpIdSwapper[TcPerson[T]]{
		def withCpId(p: TcPerson[T], id: UriId) = p.copy(cpId = id)
	}

	implicit def orgCpIdSwapper[T <: TC] = new CpIdSwapper[TcOrg[T]]{
		def withCpId(org: TcOrg[T], id: UriId) = org match{
			case ss: TcStation[T] => ss.copy(cpId = id)
			case ci: TcPlainOrg[T] => ci.copy(cpId = id)
		}
	}

	implicit def plainOrgCpIdSwapper[T <: TC] = new CpIdSwapper[TcPlainOrg[T]]{
		def withCpId(org: TcPlainOrg[T], id: UriId) = org.copy(cpId = id)
	}

	implicit def stationCpIdSwapper[T <: TC] = new CpIdSwapper[TcStation[T]]{
		def withCpId(s: TcStation[T], id: UriId) = s.copy(cpId = id)
	}

	implicit def instrCpIdSwapper[T <: TC] = new CpIdSwapper[TcInstrument[T]]{
		//noop, because instrument cpIds are expected to be stable
		def withCpId(instr: TcInstrument[T], id: UriId) = instr
	}

}

case class TcPerson[+T <: TC](
	cpId: UriId,
	tcIdOpt: Option[TcId[T]],
	fname: String,
	lname: String,
	email: Option[String],
	orcid: Option[Orcid]
) extends Entity[T]

sealed trait TcOrg[+T <: TC] extends Entity[T]{ def org: Organization }

case class TcStation[+T <: TC](
	cpId: UriId,
	tcId: TcId[T],
	core: Station,
	responsibleOrg: Option[TcPlainOrg[T]] //needed to avoid info loss with core.responsibleOrganization
) extends TcOrg[T] with TcEntity[T]{
	def org = core.org
}

case class TcPlainOrg[+T <: TC](cpId: UriId, tcIdOpt: Option[TcId[T]], org: Organization) extends TcOrg[T]

case class TcInstrument[+T <: TC : TcConf](
	tcId: TcId[T],
	model: String,
	sn: String,
	name: Option[String] = None,
	vendor: Option[TcOrg[T]] = None,
	owner: Option[TcOrg[T]] = None,
	partsCpIds: Seq[UriId] = Nil
) extends TcEntity[T]{
	//cpId for instruments is strictly related to tcId, and is expected to be stable
	def cpId = CpVocab.instrCpId(tcId)
}

class AssumedRole[+T <: TC](
	val kind: Role,
	val holder: TcPerson[T],
	val org: TcOrg[T],
	val weight: Option[Int],
	val extra: Option[String]
){
	def id = (kind.name, holder.bestId, org.bestId)
	override def toString = s"AssumedRole($kind , $holder , $org )"
}

case class Membership[+T <: TC](cpId: UriId, role: AssumedRole[T], start: Option[Instant], stop: Option[Instant])

class TcState[+T <: TC : TcConf](val stations: Seq[TcStation[T]], val roles: Seq[Membership[T]], val instruments: Seq[TcInstrument[T]]){
	def tcConf = implicitly[TcConf[T]]
}

abstract class TcMetaSource[T <: TC : TcConf]{
	type State = TcState[T]
	def state: Source[State, Any]
	def stationId(baseId: UriId) = TcConf.stationId[T](baseId)
}

object TcMetaSource{
	val defaultInstrModel = "N/A"
	val defaultSerialNum = "N/A"
}
