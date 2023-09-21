package se.lu.nateko.cp.meta.metaflow.cities

import se.lu.nateko.cp.meta.metaflow.{TC, TcConf}
import se.lu.nateko.cp.meta.services.CpmetaVocab

sealed trait CitiesTC extends TC

case object MunichMidLow extends CitiesTC
case object ParisMidLow extends CitiesTC
case object ZurichMidLow extends CitiesTC

given MunichConf: TcConf[MunichMidLow.type] with
	val tc = MunichMidLow
	val stationPrefix = "Munich"
	val tcPrefix = "Munich"

	def stationClass(meta: CpmetaVocab) = meta.munichStationClass

	def tcIdPredicate(meta: CpmetaVocab) = meta.hasMunichId


given ParisConf: TcConf[ParisMidLow.type] with
	val tc = ParisMidLow
	val stationPrefix = "Paris"
	val tcPrefix = "Paris"

	def stationClass(meta: CpmetaVocab) = meta.parisStationClass

	def tcIdPredicate(meta: CpmetaVocab) = meta.hasParisId


given ZurichConf: TcConf[ZurichMidLow.type] with
	val tc = ZurichMidLow
	val stationPrefix = "Zurich"
	val tcPrefix = "Zurich"

	def stationClass(meta: CpmetaVocab) = meta.zurichStationClass

	def tcIdPredicate(meta: CpmetaVocab) = meta.hasZurichId
