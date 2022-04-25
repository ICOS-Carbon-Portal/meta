package se.lu.nateko.cp.meta.icos

sealed trait TC

case object ATC extends TC
case object ETC extends TC
case object OTC extends TC

