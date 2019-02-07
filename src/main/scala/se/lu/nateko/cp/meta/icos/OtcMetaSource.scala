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

class OtcMetaSource(
	server: WriteNotifyingInstanceServer, log: LoggingAdapter
)(implicit envriConfigs: EnvriConfigs) extends TcMetaSource[OTC.type] {

	private type O = OTC.type
	private val otcVocab = new OtcMetaVocab(server.factory)
	private def makeId(iri: IRI): TcId[O] = implicitly[TcConf[O]].makeId(iri.getLocalName)

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
			try{
				immutable.Iterable(readState)
			}catch{
				case err: Throwable =>
					log.error(err, "Error while reading OTC metadata")
					Nil
			}
		}

	def readState: TcState[O] = {
		val instruments = reader.getOtcInstruments
		val allRoles = reader.getAssumedRoles
		val tcRoles = allRoles.flatMap(toTcRole)
		val tcStations = allRoles.flatMap(toPiInfo).groupBy(_._1).toSeq.collect{
			case (station, tuples) if !tuples.isEmpty =>
				val piSeq: Seq[Person[O]] = tuples.map(_._2)
				val pis = OneOrMorePis(piSeq.head, piSeq.tail: _*)
				new TcStation[O](station, pis)
		}
		new TcState(tcStations, tcRoles, instruments)
	}

	private object reader extends IcosMetaInstancesFetcher(server.inner){

		/**
		 * important override changing the default behaviour of RdfReader
		 */
		override protected def getTcId[T <: TC](uri: IRI)(implicit tcConf: TcConf[T]): Option[TcId[T]] = {
			Some(tcConf.makeId(uri.getLocalName))
		}

		def getOtcInstruments: Seq[Instrument[O]] = getInstruments[O].map{instr =>
			instr.copy(cpId = TcConf.tcScopedId[O](instr.cpId))
		}

		def getAssumedRoles: IndexedSeq[AssumedRole[O]] = getDirectClassMembers(otcVocab.assumedRoleClass).flatMap{arIri =>
			for(
				persIri <- getOptionalUri(arIri, otcVocab.hasHolder);
				person <- Try(getPerson(makeId(persIri), persIri)).toOption;
				roleIri <- getOptionalUri(arIri, otcVocab.hasRole);
				role <- getRole(roleIri);
				orgIri <- getOptionalUri(arIri, otcVocab.atOrganization);
				org0 <- getOrganization[O](orgIri);
				org = makeCpIdOtcSpecific(addJsonIfMobileStation(org0, orgIri))
			) yield new AssumedRole(role, person, org)
		}.toIndexedSeq

		private def addJsonIfMobileStation(org: Organization[O], iri: IRI): Organization[O] = org match {
			case ms: CpMobileStation[O] =>
				ms.copy(geoJson = getOptionalString(iri, otcVocab.spatialReference))
			case other => other
		}

		private def makeCpIdOtcSpecific(org: Organization[O]): Organization[O] = org match {
			case _: CpStation[O] => org.withCpId(TcConf.stationId[O](org.cpId))
			case _ => org
		}
	}

	private def toTcRole(ar: AssumedRole[O]): Option[TcAssumedRole[O]] = ar.role match{
		case npr: NonPiRole => Some(new TcAssumedRole(npr, ar.holder, ar.org))
		case _ => None
	}

	private def toPiInfo(ar: AssumedRole[O]): Option[(CpStation[O], Person[O])] = ar.org match{
		case s: CpStation[O] if ar.role == PI => Some(s -> ar.holder)
		case _ => None
	}
}


class OtcMetaVocab(val factory: ValueFactory) extends CustomVocab{

	implicit val bup = makeUriProvider("http://meta.icos-cp.eu/ontologies/otcmeta/")

	val hasHolder = getRelative("hasHolder")
	val hasRole = getRelative("hasRole")
	val atOrganization = getRelative("atOrganization")

	val spatialReference = getRelative("hasSpatialReference")

	val assumedRoleClass = getRelative("AssumedRole")

}
