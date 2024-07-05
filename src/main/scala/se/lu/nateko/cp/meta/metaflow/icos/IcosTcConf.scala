package se.lu.nateko.cp.meta.metaflow.icos

import se.lu.nateko.cp.meta.metaflow.{TC, TcConf}
import se.lu.nateko.cp.meta.services.CpmetaVocab

sealed trait IcosTC extends TC

case object ATC extends IcosTC
case object ETC extends IcosTC
case object OTC extends IcosTC

given AtcConf: TcConf[ATC.type] with
	val tc = ATC
	val stationPrefix = "AS"
	val tcPrefix = "ATC"

	def stationClass(meta: CpmetaVocab) = meta.atmoStationClass

	def tcIdPredicate(meta: CpmetaVocab) = meta.hasAtcId


given EtcConf: TcConf[ETC.type] with
	val tc = ETC
	val stationPrefix = "ES"
	val tcPrefix = "ETC"

	def stationClass(meta: CpmetaVocab) = meta.ecoStationClass

	def tcIdPredicate(meta: CpmetaVocab) = meta.hasEtcId


given OtcConf: TcConf[OTC.type] with
	val tc = OTC
	val stationPrefix = "OS"
	val tcPrefix = "OTC"

	def stationClass(meta: CpmetaVocab) = meta.oceStationClass

	def tcIdPredicate(meta: CpmetaVocab) = meta.hasOtcId
