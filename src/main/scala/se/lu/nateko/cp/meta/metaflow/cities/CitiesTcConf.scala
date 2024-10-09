package se.lu.nateko.cp.meta.metaflow.cities

import se.lu.nateko.cp.meta.metaflow.{TC, TcConf}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.core.data.CityNetwork
import org.eclipse.rdf4j.model.IRI


sealed trait CitiesTC(val network: CityNetwork) extends TC

case object MunichMidLow extends CitiesTC("Munich")
case object ParisMidLow extends CitiesTC("Paris")
case object ZurichMidLow extends CitiesTC("Zurich")

sealed class CityTcConf[CTC <: CitiesTC](
	val tc: CTC,
	classGetter: CpmetaVocab => IRI,
	predGetter: CpmetaVocab => IRI
) extends TcConf[CTC]:
	val stationPrefix = tc.network
	val tcPrefix = tc.network
	def stationClass(meta: CpmetaVocab): IRI = classGetter(meta)
	def tcIdPredicate(meta: CpmetaVocab): IRI = predGetter(meta)

given MunichConf: TcConf[MunichMidLow.type] =
	CityTcConf(MunichMidLow, _.munichStationClass, _.hasMunichId)

given ParisConf: TcConf[ParisMidLow.type] =
	CityTcConf(ParisMidLow, _.parisStationClass, _.hasParisId)

given ZurichConf: TcConf[ZurichMidLow.type] =
	CityTcConf(ZurichMidLow, _.zurichStationClass, _.hasZurichId)
