package se.lu.nateko.cp.meta.metaflow.cities

import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.MetaUploadConf
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.metaflow.*
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.utils.Validated

class MidLowCostMetaSource[T <: CitiesTC : TcConf](
	conf: MetaUploadConf,
	countryCode: CountryCode
)(using ActorSystem) extends FileDropMetaSource[T](conf):

	override def readState: Validated[State] =
		//TODO Implement
		???

end MidLowCostMetaSource

val stationsTableName = "sites"