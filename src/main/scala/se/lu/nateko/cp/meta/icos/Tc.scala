package se.lu.nateko.cp.meta.icos

sealed trait TC{
	type Pis <: NumberOfPis[this.type]
}

object ATC extends TC{type Pis = OneOrMorePis[ATC.type]}
object ETC extends TC{type Pis = SinglePi[ETC.type]}
object OTC extends TC{type Pis = OneOrMorePis[OTC.type]}


sealed trait NumberOfPis[+T <: TC]{ def all: List[Person[T]]}

final case class SinglePi[+T <: TC](one: Person[T]) extends NumberOfPis[T]{
	def all = one :: Nil
}

final case class OneOrMorePis[+T <: TC](first: Person[T], rest: Person[T]*) extends NumberOfPis[T]{
	def all = first :: rest.toList
}

