package se.lu.nateko.cp.meta.icos

import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import org.reactivestreams.Publisher
import se.lu.nateko.cp.meta.api.CustomVocab
import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import org.eclipse.rdf4j.model.IRI
import scala.util.Try

class OtcMetaSource(
	server: InstanceServer, notifier: Publisher[Any]
)(implicit envriConfigs: EnvriConfigs) extends TcMetaSource[OTC.type] {

	private type O = OTC.type
	private val otcVocab = new OtcMetaVocab(server.factory)
	private def makeId(iri: IRI): TcId[O] = implicitly[TcConf[O]].makeId(iri.getLocalName)

	def state: Source[TcState[O], Any] = {
		???
	}

	def readState: TcState[O] = {
		val instruments = reader.getInstruments[O]
		val allRoles = reader.getAssumedRoles
		val tcRoles = allRoles.flatMap(toTcRole)
		val tcStations = allRoles.flatMap(toPiInfo).groupBy(_._1).toSeq.collect{
			case (station, tuples) if tuples.length > 1 =>
				val piSeq: Seq[Person[O]] = tuples.map(_._2)
				val pis = OneOrMorePis(piSeq.head, piSeq.tail: _*)
				new TcStation[O](station, pis)
		}
		new TcState(tcStations, tcRoles, instruments)
	}

	private object reader extends IcosMetaInstancesFetcher(server){

		override protected def getTcId[T <: TC](uri: IRI)(implicit tcConf: TcConf[T]): Option[TcId[T]] = {
			Some(tcConf.makeId(uri.getLocalName))
		}

		def getAssumedRoles: IndexedSeq[AssumedRole[O]] = getDirectClassMembers(otcVocab.assumedRoleClass).flatMap{arIri =>
			for(
				persIri <- getOptionalUri(arIri, otcVocab.hasHolder);
				person <- Try(getPerson(makeId(persIri), persIri)).toOption;
				roleIri <- getOptionalUri(arIri, otcVocab.hasRole);
				role <- getRole(roleIri);
				orgIri <- getOptionalUri(arIri, otcVocab.atOrganization);
				org <- getOrganization[O](orgIri)
			) yield new AssumedRole(role, person, org)
		}.toIndexedSeq
	}

	private def toTcRole(ar: AssumedRole[O]): Option[TcAssumedRole[O]] = ar.role match{
		case npr: NonPiRole => Some(new TcAssumedRole(npr, ar.holder, ar.org))
		case _ => None
	}

	private def toPiInfo(ar: AssumedRole[O]): Option[(CpStation[O], Person[O])] = ar.org match{
		case s: CpStation[O] if ar.role != PI => Some(s -> ar.holder)
		case _ => None
	}
}


class OtcMetaVocab(val factory: ValueFactory) extends CustomVocab{

	implicit val bup = makeUriProvider("http://meta.icos-cp.eu/ontologies/otcmeta/")

	val hasHolder = getRelative("hasHolder")
	val hasRole = getRelative("hasRole")
	val atOrganization = getRelative("atOrganization")

	val assumedRoleClass = getRelative("AssumedRole")

}
