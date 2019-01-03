package se.lu.nateko.cp.meta.icos

import java.time.Instant

import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.core.data.Position

sealed trait TcId[+T <: TC]{
	def id: String
}

case class AtcId(id: String) extends TcId[ATC.type]
case class EtcId(id: String) extends TcId[ETC.type]
case class OtcId(id: String) extends TcId[OTC.type]

sealed trait Entity[+T <: TC]{
	def cpId: String
	def tcId: TcId[T]
	def withCpId(id: String): this.type = ???
}

case class Person[+T <: TC](cpId: String, tcId: TcId[T], fname: String, lName: String, email: Option[String]) extends Entity[T]

sealed trait NumberOfPis

final case class SinglePi[+T <: TC](one: Person[T]) extends NumberOfPis

final case class OneOrMorePis[+T <: TC](first: Person[T], rest: Person[T]*) extends NumberOfPis{
	def all = first :: rest.toList
}

sealed trait Role{
	def name: String
}

sealed abstract class NonPiRole(val name: String) extends Role

case object PI extends Role{def name = "PI"}
case object Researcher extends NonPiRole("Researcher")

sealed trait Organization[+T <: TC] extends Entity[T]{ def name: String }

sealed trait Station[+T <: TC] extends Organization[T]{ def id: String }
sealed trait CpStation[+T <: TC] extends Station[T]

class CpStationaryStation[+T <: TC](
	val cpId: String,
	val tcId: TcId[T],
	val name: String,
	val id: String,
	val pos: Position
) extends CpStation[T]

class CpMobileStation(
	val cpId: String,
	val tcId: TcId[OTC.type],
	val name: String,
	val id: String,
	val geoJson: Option[String]
) extends CpStation[OTC.type]

case class TcStation[+T <: TC](station: CpStation[T], pi: T#Pis) extends Station[T]{
	def cpId = station.cpId
	def tcId = station.tcId
	def name = station.name
	def id = station.id
}

class CompanyOrInstitution[+T <: TC](val cpId: String, val tcId: TcId[T], val name: String, val label: Option[String]) extends Organization[T]

case class Instrument[+T <: TC](
	cpId: String,
	tcId: TcId[T],
	model: String,
	sn: String,
	name: Option[String] = None,
	vendor: Option[Organization[T]] = None,
	owner: Option[Organization[T]] = None,
	parts: Seq[Instrument[T]] = Nil
) extends Entity[T]

trait AssumedRole[+T <: TC]{
	def role: Role
	def holder: Person[T]
	def org: Organization[T]
}

case class TcAssumedRole[+T <: TC](role: NonPiRole, holder: Person[T], org: Organization[T]) extends AssumedRole[T]
case class Membership[+T <: TC](role: AssumedRole[T], start: Option[Instant], stop: Option[Instant])

class CpTcState[+T <: TC](val stations: Seq[CpStation[T]], val roles: Seq[Membership[T]], val instruments: Seq[Instrument[T]])
class TcState[+T <: TC](val stations: Seq[TcStation[T]], val roles: Seq[TcAssumedRole[T]], val instruments: Seq[Instrument[T]])

class AllTcStates(val atc: CpTcState[ATC.type], val etc: CpTcState[ETC.type], val otc: CpTcState[OTC.type])

sealed trait TC {
	type Pis <: NumberOfPis
}

object ATC extends TC{type Pis = OneOrMorePis[ATC.type]}
object ETC extends TC{type Pis = SinglePi[ETC.type]}
object OTC extends TC{type Pis = OneOrMorePis[OTC.type]}

abstract class TcMetaSource[T <: TC]{
	type State = TcState[T]
	type Station = TcStation[T]
	def state: Source[State, Any]
}
