package se.lu.nateko.cp.meta.icos

import java.time.Instant

import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.core.data.Position

sealed trait Entity{
	def cpId: String
	def tcId: String
}

case class Person(cpId: String, tcId: String, fname: String, lastName: String, email: Option[String]) extends Entity

sealed trait OneOrMorePis

final case class SinglePi(one: Person) extends OneOrMorePis

final case class MoreThanOnePi(first: Person, second: Person, rest: Person*) extends OneOrMorePis{
	def all = first :: second :: rest.toList
}

sealed trait Role{
	def name: String
}

sealed abstract class NonPiRole(val name: String) extends Role

case object PI extends Role{def name = "PI"}
case object ResearcherAt extends NonPiRole("is researcher at")

sealed trait Organization extends Entity{
	def name: String
}

trait Station extends Organization{
	def pos: Position
	def pi: OneOrMorePis
}

case class Institution(cpId: String, tcId: String, name: String) extends Organization
case class Manufacturer(cpId: String, tcId: String, name: String) extends Entity

case class Instrument(
	id: String,
	model: String,
	sn: Option[String] = None,
	vendor: Option[Manufacturer] = None,
	owner: Option[Institution] = None,
	parts: Seq[Instrument] = Nil
)

trait AssumedRole{
	def role: Role
	def holder: Person
	def org: Organization
}
case class TcAssumedRole(role: NonPiRole, holder: Person, org: Organization) extends AssumedRole
case class Membership(role: AssumedRole, start: Option[Instant], stop: Option[Instant])

class State(val stations: Seq[Station], val roles: Seq[Membership])

trait TcMetaSource {

	type Pis <: OneOrMorePis

	case class TcStation(cpId: String, tcId: String, name: String, pos: Position, pi: Pis) extends Station

	class TcState(val stations: Seq[TcStation], val roles: Seq[TcAssumedRole], val instruments: Seq[Instrument])

	def state: Source[TcState, Any]
}
