package se.lu.nateko.cp.meta.metaflow

import akka.stream.scaladsl.Source
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.{Funder, Funding, Orcid, Organization, Position, Station}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}

import java.time.Instant
import se.lu.nateko.cp.meta.core.data.Network


trait TC
sealed trait TcId[+T <: TC]{
	def id: String
}

trait TcConf[+T <: TC]{
	private case class Id(val id: String) extends TcId[T]
	def makeId(id: String): TcId[T] = new Id(id)
	def tc: T
	def stationPrefix: String
	def tcPrefix: String
	def stationClass(meta: CpmetaVocab): IRI
	def tcIdPredicate(meta: CpmetaVocab): IRI
}

object TcConf:
	def makeId[T <: TC](id: String)(using conf: TcConf[T]): TcId[T] = conf.makeId(id)
	def stationId[T <: TC](baseId: UriId)(using tc: TcConf[T]) = UriId(s"${tc.stationPrefix}_${baseId.urlSafeString}")
	def tcScopedId[T <: TC](baseId: UriId)(using tc: TcConf[T]) = UriId(s"${tc.tcPrefix}_${baseId.urlSafeString}")


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

	extension [E: CpIdSwapper](e: E){
		def withCpId(id: UriId): E = summon[CpIdSwapper[E]].withCpId(e, id)
	}

	given [T <: TC]: CpIdSwapper[TcPerson[T]] with{
		def withCpId(p: TcPerson[T], id: UriId) = p.copy(cpId = id)
	}

	given [T <: TC]: CpIdSwapper[TcOrg[T]] with{
		def withCpId(org: TcOrg[T], id: UriId) = org match{
			case ss: TcStation[T] => ss.copy(cpId = id)
			case go: TcGenericOrg[T] => go.copy(cpId = id)
			case fu: TcFunder[T] => fu.copy(cpId = id)
		}
	}

	given [T <: TC]: CpIdSwapper[TcFunder[T]] with{
		def withCpId(org: TcFunder[T], id: UriId) = org.copy(cpId = id)
	}

	given [T <: TC]: CpIdSwapper[TcPlainOrg[T]] with{
		def withCpId(org: TcPlainOrg[T], id: UriId) = org match{
			case go: TcGenericOrg[T] => go.copy(cpId = id)
			case fu: TcFunder[T] => fu.copy(cpId = id)
		}
	}

	given [T <: TC]: CpIdSwapper[TcStation[T]] with{
		def withCpId(s: TcStation[T], id: UriId) = s.copy(cpId = id)
	}

	given [T <: TC]: CpIdSwapper[TcInstrument[T]] with{
		//noop, because instrument cpIds are expected to be stable
		def withCpId(instr: TcInstrument[T], id: UriId) = instr
	}

	given [T <: TC]: CpIdSwapper[InstrumentDeployment[T]] with{
		//swapping station info, not deployments own cpid
		def withCpId(depl: InstrumentDeployment[T], id: UriId) = depl.copy(stationUriId = id)
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

case class TcNetwork[+T <: TC](cpId: UriId, core: Network)

case class TcStation[+T <: TC](
	cpId: UriId,
	tcId: TcId[T],
	core: Station,
	responsibleOrg: Option[TcPlainOrg[T]], //needed to avoid info loss with core.responsibleOrganization
	funding: Seq[TcFunding[T]], // needed to avoid info loss with core.funding
	networks: Seq[TcNetwork[T]]
) extends TcOrg[T] with TcEntity[T]{
	def org = core.org
}

sealed trait TcPlainOrg[+T <: TC] extends TcOrg[T]
case class TcGenericOrg[+T <: TC](cpId: UriId, tcIdOpt: Option[TcId[T]], org: Organization) extends TcPlainOrg[T]
case class TcFunder[+T <: TC](cpId: UriId, tcIdOpt: Option[TcId[T]], core: Funder) extends TcPlainOrg[T]{
	def org = core.org
}

case class TcInstrument[+T <: TC : TcConf](
	tcId: TcId[T],
	model: String,
	sn: String,
	name: Option[String] = None,
	comment: Option[String] = None,
	vendor: Option[TcOrg[T]] = None,
	owner: Option[TcOrg[T]] = None,
	partsCpIds: Seq[UriId] = Nil,
	deployments: Seq[InstrumentDeployment[T]]
) extends TcEntity[T]{
	//cpId for instruments is strictly related to tcId, and is expected to be stable
	def cpId = CpVocab.instrCpId(tcId)
}

case class InstrumentDeployment[+T <: TC](
	cpId: UriId,
	stationTcId: TcId[T],
	stationUriId: UriId,
	pos: Option[Position],
	variable: Option[String],
	start: Option[Instant],
	stop: Option[Instant]
) extends Entity[T]{
	def tcIdOpt: Option[TcId[T]] = Some(stationTcId)
}

case class TcFunding[+T <: TC](cpId: UriId, funder: TcFunder[T], core: Funding)

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

trait TcMetaSource[T <: TC : TcConf]:
	type State = TcState[T]
	def state: Source[State, () => Unit]
	def stationId(baseId: UriId) = TcConf.stationId[T](baseId)

object TcMetaSource:
	val defaultInstrModel = "N/A"
	val defaultSerialNum = "N/A"
