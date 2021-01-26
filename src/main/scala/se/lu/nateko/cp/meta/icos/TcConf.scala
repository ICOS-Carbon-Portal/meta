package se.lu.nateko.cp.meta.icos

import org.eclipse.rdf4j.model.IRI

import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.api.UriId


sealed trait TcId[+T <: TC]{
	def id: String
}

sealed trait TcConf[+T <: TC]{
	private case class Id(val id: String) extends TcId[T]
	def makeId(id: String): TcId[T] = new Id(id)
	def tc: T
	def stationPrefix: String
	def tcPrefix: String
	def stationClass(meta: CpmetaVocab): IRI
	def tcIdPredicate(meta: CpmetaVocab): IRI
}

object TcConf{

	implicit object AtcConf extends TcConf[ATC.type]{

		val tc = ATC
		val stationPrefix = "AS"
		val tcPrefix = "ATC"

		def stationClass(meta: CpmetaVocab) = meta.atmoStationClass

		def tcIdPredicate(meta: CpmetaVocab) = meta.hasAtcId
	}

	implicit object EtcConf extends TcConf[ETC.type]{

		val tc = ETC
		val stationPrefix = "ES"
		val tcPrefix = "ETC"

		def stationClass(meta: CpmetaVocab) = meta.ecoStationClass

		def tcIdPredicate(meta: CpmetaVocab) = meta.hasEtcId

	}

	implicit object OtcConf extends TcConf[OTC.type]{

		val tc = OTC
		val stationPrefix = "OS"
		val tcPrefix = "OTC"

		def stationClass(meta: CpmetaVocab) = meta.oceStationClass

		def tcIdPredicate(meta: CpmetaVocab) = meta.hasOtcId
	}

	def makeId[T <: TC](id: String)(implicit conf: TcConf[T]): TcId[T] = conf.makeId(id)
	def stationId[T <: TC](baseId: UriId)(implicit tc: TcConf[T]) = UriId(s"${tc.stationPrefix}_${baseId.urlSafeString}")
	def tcScopedId[T <: TC](baseId: UriId)(implicit tc: TcConf[T]) = UriId(s"${tc.tcPrefix}_${baseId.urlSafeString}")
}
