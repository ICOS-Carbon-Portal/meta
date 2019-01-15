package se.lu.nateko.cp.meta.icos

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS

import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.etcupload.{ StationId => EtcStationId }
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.EnrichedValueFactory
import java.time.Instant

class RdfMaker(vocab: CpVocab, meta: CpmetaVocab) {

	private implicit val envri = Envri.ICOS

	def getStatements[T <: TC : TcConf](memb: Membership[T]): Seq[Statement] = {
		val uri = vocab.getMembership(memb.cpId)

		val triples: Seq[(IRI, IRI, Value)] = {
			(uri, RDF.TYPE, meta.membershipClass) +:
			(uri, meta.atOrganization, getIri(memb.role.org)) +:
			(uri, meta.hasRole, vocab.getRole(memb.role.role.name)) +:
			(getIri(memb.role.holder), meta.hasMembership, uri) +:
			memb.start.toSeq.map{inst =>
				(uri, meta.hasStartTime, vocab.lit(inst))
			} ++:
			memb.stop.toSeq.map{inst =>
				(uri, meta.hasEndTime, vocab.lit(inst))
			} ++:
			Nil
		}
		triples.map(vocab.factory.tripleToStatement)
	}

	def getMembershipEnd(membId: String): Statement = {
		val uri = vocab.getMembership(membId)
		vocab.factory.createStatement(uri, meta.hasEndTime, vocab.lit(Instant.now))
	}

	def getStatements[T <: TC : TcConf](e: Entity[T]): Seq[Statement] = {

		val uri = getIri(e)

		val triples: Seq[(IRI, IRI, Value)] = e match{

			case p: Person[T] =>
				(uri, RDF.TYPE, meta.personClass) +:
				(uri, meta.hasFirstName, vocab.lit(p.fname)) +:
				(uri, meta.hasLastName, vocab.lit(p.lName)) +:
				p.email.map{email =>
					(uri, meta.hasEmail, vocab.lit(email))
				}.toList

			case s: CpStationaryStation[T] =>
				(uri, meta.hasLatitude, vocab.lit(s.pos.lat)) +:
				(uri, meta.hasLongitude, vocab.lit(s.pos.lon)) +:
				s.pos.alt.map{alt =>
					(uri, meta.hasElevation, vocab.lit(alt))
				} ++:
				stationTriples(s)

			case s: CpMobileStation[T] =>
				//TODO Add the json value triple
				stationTriples(s)

			case ci: CompanyOrInstitution[T] =>
				(uri, RDF.TYPE, meta.orgClass) ::
				(uri, meta.hasName, vocab.lit(ci.name)) ::
				ci.label.toList.map{label =>
					(uri, RDFS.LABEL, vocab.lit(label))
				}

			case instr: Instrument[T] =>
				(uri, RDF.TYPE, meta.instrumentClass) +:
				(uri, meta.hasModel, vocab.lit(instr.model)) +:
				(uri, meta.hasSerialNumber, vocab.lit(instr.sn)) +:
				instr.name.toSeq.map{name =>
					(uri, meta.hasName, vocab.lit(name))
				} ++:
				instr.owner.toSeq.map{owner =>
					(uri, meta.hasInstrumentOwner, getIri(owner))
				} ++:
				instr.vendor.toSeq.map{vendor =>
					(uri, meta.hasVendor, getIri(vendor))
				} ++:
				instr.partsCpIds.map{cpId =>
					(uri, meta.dcterms.hasPart, vocab.getIcosInstrument(cpId))
				}
		}

		val tcIdPredicate: IRI = implicitly[TcConf[T]].tcIdPredicate(meta)

		val tcIdTriple = (uri, tcIdPredicate, vocab.lit(e.tcId.id))
		(triples :+ tcIdTriple).map(vocab.factory.tripleToStatement)
	}

	private def getIri[T <: TC : TcConf](e: Entity[T]): IRI =  e match{

		case p: Person[T] =>
			vocab.getPerson(p.cpId)

		case s: CpStation[T] => implicitly[TcConf[T]].makeStation(vocab, s)

		case ci: CompanyOrInstitution[T] =>
			vocab.getOrganization(ci.cpId)

		case instr: Instrument[T] =>
			vocab.getIcosInstrument(instr.cpId)
	}

	private def stationTriples[T <: TC : TcConf](s: CpStation[T]): List[(IRI, IRI, Value)] = {
		val uri = getIri(s)
		val stationClass = implicitly[TcConf[T]].stationClass(meta)
		(uri, RDF.TYPE, stationClass) +:
		(uri, meta.hasStationId, vocab.lit(s.id)) +:
		(uri, meta.hasName, vocab.lit(s.name)) +: 
		Nil
	}
}
