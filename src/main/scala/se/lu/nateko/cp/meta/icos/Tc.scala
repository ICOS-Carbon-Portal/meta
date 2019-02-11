package se.lu.nateko.cp.meta.icos

sealed trait TC

final case object ATC extends TC
final case object ETC extends TC
final case object OTC extends TC

