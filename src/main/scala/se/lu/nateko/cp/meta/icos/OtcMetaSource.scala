package se.lu.nateko.cp.meta.icos

import scala.collection.immutable
import scala.concurrent.duration.DurationInt
import scala.util.Try

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory

import akka.actor.Status
import akka.event.LoggingAdapter
import akka.stream.OverflowStrategy
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import se.lu.nateko.cp.meta.instanceserver.WriteNotifyingInstanceServer
import se.lu.nateko.cp.meta.utils.Validated

class OtcMetaSource(
	server: WriteNotifyingInstanceServer, log: LoggingAdapter
)(implicit envriConfigs: EnvriConfigs) extends TcMetaSource[OTC.type] {

	private type O = OTC.type

	def state: Source[TcState[O], () => Unit] = Source
		.actorRef(1, OverflowStrategy.dropHead)
		.mapMaterializedValue{actor =>
			server.setSubscriber(() => actor ! 2) //cost of single update is 2 units
			() => actor ! Status.Success
		}
		.prepend(Source.single(2)) //triggering initial state reading at the stream startup
		.conflate(Keep.right) //swallow throttle's back-pressure
		.throttle(2, 1.minute, 1, identity, ThrottleMode.Shaping) //2 units of cost per minute
		.mapConcat[TcState[O]]{_ =>
			val stateV = Validated(readState).flatMap(identity)

			if(!stateV.errors.isEmpty){
				val errKind = if(stateV.result.isEmpty) "Hard error" else "Problems"
				log.warning(s"$errKind while reading OTC metadata:\n${stateV.errors.mkString("\n")}")
			}
			stateV.result.toList
		}

	def readState: Validated[TcState[O]] = for(
		instruments <- reader.getOtcInstruments;
		allMembs <- reader.getOtcMemberships;
		stations = allMembs.map(_.role.org).collect{
			case s: CpStation[O] => s
		}
	) yield new TcState(stations, allMembs, instruments)

	private object reader extends IcosMetaInstancesFetcher(server.inner){

		/**
		 * important override changing the default behaviour of RdfReader
		 */
		override protected def getTcId[T <: TC](uri: IRI)(implicit tcConf: TcConf[T]): Option[TcId[T]] = {
			Some(tcConf.makeId(uri.getLocalName))
		}

		def getOtcInstruments: Validated[Seq[Instrument[O]]] = getInstruments[O].map(_.map{instr =>
			instr.copy(cpId = TcConf.tcScopedId[O](instr.cpId))
		})

		def getOtcMemberships: Validated[IndexedSeq[Membership[O]]] = getMemberships[O].map(_.map{memb =>
			val r = memb.role
			memb.copy(role = new AssumedRole[O](r.kind, r.holder, makeCpIdOtcSpecific(r.org)))
		}.toIndexedSeq)

		private def makeCpIdOtcSpecific(org: Organization[O]): Organization[O] = org match {
			case _: CpStation[O] => org.withCpId(TcConf.stationId[O](org.cpId))
			case _ => org
		}
	}
}
