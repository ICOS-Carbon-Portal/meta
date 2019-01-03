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

class RdfMaker(vocab: CpVocab, meta: CpmetaVocab) {

	private implicit val envri = Envri.ICOS

	def getStatements[T <: TC](e: Entity[T]): Seq[Statement] = {

		def getTriples(e: Entity[T]): Seq[(IRI, IRI, Value)] = e match{

			case p: Person[T] =>
				val uri = vocab.getPerson(p.cpId)
				(uri, RDF.TYPE, meta.personClass) +:
				tcIdTriple(p, uri) +:
				(uri, meta.hasFirstName, vocab.lit(p.fname)) +:
				(uri, meta.hasLastName, vocab.lit(p.lName)) +:
				p.email.map{email =>
					(uri, meta.hasEmail, vocab.lit(email))
				}.toList

			case s: TcStation[T] =>
				getTriples(s.station)

			case s: CpStationaryStation[T] =>
				val baseTriples = stationTriples(s)
				val uri = baseTriples.head._1
				(uri, meta.hasLatitude, vocab.lit(s.pos.lat)) +:
				(uri, meta.hasLongitude, vocab.lit(s.pos.lon)) +:
				s.pos.alt.map{alt =>
					(uri, meta.hasElevation, vocab.lit(alt))
				} ++:
				baseTriples

			case s: CpMobileStation =>
				stationTriples(s)

			case ci: CompanyOrInstitution[T] =>
				val uri = vocab.getOrganization(ci.cpId)
				(uri, RDF.TYPE, meta.orgClass) ::
				(uri, meta.hasName, vocab.lit(ci.name)) ::
				tcIdTriple(ci, uri) ::
				ci.label.toList.map{label =>
					(uri, RDFS.LABEL, vocab.lit(label))
				}

			case instr: Instrument[T] =>
				val uri = vocab.getIcosInstrument(instr.cpId)
				(uri, RDF.TYPE, meta.instrumentClass) +:
				tcIdTriple(instr, uri) +:
				(uri, meta.hasModel, vocab.lit(instr.model)) +:
				(uri, meta.hasSerialNumber, vocab.lit(instr.sn)) +:
				instr.name.toSeq.map{name =>
					(uri, meta.hasName, vocab.lit(name))
				} ++:
				instr.owner.toSeq.map{owner =>
					(uri, meta.hasInstrumentOwner, vocab.getOrganization(owner.cpId))
				} ++:
				instr.parts.map{part =>
					(uri, meta.dcterms.hasPart, vocab.getIcosInstrument(part.cpId))
				}
		}
		getTriples(e).map(vocab.factory.tripleToStatement)
	}

	private def tcIdTriple[T <: TC](e: Entity[T], subj: IRI): (IRI, IRI, Value) = {
		val tcIdPredicate: IRI = e.tcId match{
			case _: AtcId => meta.hasAtcId
			case _: EtcId => meta.hasEtcId
			case _: OtcId => meta.hasOtcId
		}
		(subj, tcIdPredicate, vocab.lit(e.tcId.id))
	}

	private def stationTriples[T <: TC](s: CpStation[T]): List[(IRI, IRI, Value)] = {
		val (uri, stationClass) = s.tcId match{
			case _: AtcId =>
				(vocab.getAtmosphericStation(s.cpId), meta.atmoStationClass)
			case _: EtcId =>
				val EtcStationId(stId) = s.cpId
				(vocab.getEcosystemStation(stId), meta.ecoStationClass)
			case _: OtcId =>
				(vocab.getOceanStation(s.cpId), meta.oceStationClass)
		}
		(uri, RDF.TYPE, stationClass) +:
		(uri, meta.hasStationId, vocab.lit(s.id)) +:
		tcIdTriple(s, uri) +:
		(uri, meta.hasName, vocab.lit(s.name)) +: 
		Nil
	}
}
