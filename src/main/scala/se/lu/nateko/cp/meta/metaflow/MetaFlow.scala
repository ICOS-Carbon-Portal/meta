package se.lu.nateko.cp.meta.metaflow

import akka.actor.ActorSystem
import akka.stream.IOResult
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.CitiesMetaFlowConfig
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.IcosMetaFlowConfig
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.flattenToSeq
import se.lu.nateko.cp.meta.metaflow.cities.CitiesMetaFlow
import se.lu.nateko.cp.meta.metaflow.icos.IcosMetaFlow

import java.nio.file.Path
import scala.concurrent.Future
import scala.util.Try

trait MetaUploadService:
	def getTableSink(tableId: String, user: UserId): Try[Sink[ByteString, Future[IOResult]]]
	def directory: Path
	def dirName: String

class MetaFlow(val uploadServices: Seq[MetaUploadService], val cancel: () => Unit)

object MetaFlow:

	def initiate(db: MetaDb, conf: CpmetaConfig)(using Materializer, ActorSystem, EnvriConfigs): Try[MetaFlow] = Try:

		val flows: Seq[MetaFlow] = conf.instanceServers.metaFlow.flattenToSeq.map:

			case icosConf: IcosMetaFlowConfig =>
				IcosMetaFlow.init(db, conf.dataUploadService.etc, icosConf)

			case cityConf: CitiesMetaFlowConfig =>
				CitiesMetaFlow.init(db, cityConf)

		join(flows)

	end initiate


	def join(flows: Seq[MetaFlow]): MetaFlow = flows match
		case Seq(single) => single
		case _ => MetaFlow(
				uploadServices = flows.flatMap(_.uploadServices),
				cancel = () => flows.foreach(_.cancel())
			)

end MetaFlow
