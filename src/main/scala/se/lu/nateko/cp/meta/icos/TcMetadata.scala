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

sealed trait NumberOfPis[+T <: TC]{ def all: List[Person[T]]}

final case class SinglePi[+T <: TC](one: Person[T]) extends NumberOfPis[T]{
	def all = one :: Nil
}

final case class OneOrMorePis[+T <: TC](first: Person[T], rest: Person[T]*) extends NumberOfPis[T]{
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

class TcStation[+T <: TC](val station: CpStation[T], val pi: T#Pis)

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

class AssumedRole[T <: TC](val role: Role, val holder: Person[T], val org: Organization[T]){
	def id = (role.name, holder.tcId, org.tcId)
	def update(newHolder: Person[T], newOrg: Organization[T]) = new AssumedRole(role, newHolder, newOrg)
}

class TcAssumedRole[T <: TC](role: NonPiRole, holder: Person[T], org: Organization[T]) extends AssumedRole(role, holder, org)
case class Membership[T <: TC](cpId: String, role: AssumedRole[T], start: Option[Instant], stop: Option[Instant])

class CpTcState[T <: TC](val stations: Seq[CpStation[T]], val roles: Seq[Membership[T]], val instruments: Seq[Instrument[T]])
class TcState[T <: TC](val stations: Seq[TcStation[T]], val roles: Seq[TcAssumedRole[T]], val instruments: Seq[Instrument[T]])

sealed trait TC {
	type Pis <: NumberOfPis[this.type]
}

object ATC extends TC{type Pis = OneOrMorePis[ATC.type]}
object ETC extends TC{type Pis = SinglePi[ETC.type]}
object OTC extends TC{type Pis = OneOrMorePis[OTC.type]}

abstract class TcMetaSource[T <: TC]{
	type State = TcState[T]
	type Station = TcStation[T]
	def state: Source[State, Any]
}
