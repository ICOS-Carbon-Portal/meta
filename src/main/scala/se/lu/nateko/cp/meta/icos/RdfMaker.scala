package se.lu.nateko.cp.meta.icos

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF

import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.etcupload.{ StationId => EtcStationId }
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.EnrichedValueFactory

class RdfMaker(vocab: CpVocab, meta: CpmetaVocab) {

	private implicit val envri = Envri.ICOS

	def getStatements[T <: TC](e: Entity[T]): Seq[Statement] = {
		val tcIdPredicate = e.tcId match{
			case _: AtcId => meta.hasAtcId
			case _: EtcId => meta.hasEtcId
			case _: OtcId => meta.hasOtcId
		}


		def getTriples(e: Entity[T]): Seq[(IRI, IRI, Value)] = e match{

			case p: Person[T] =>
				val uri = vocab.getPerson(p.cpId)
				(uri, RDF.TYPE, meta.personClass) +:
				(uri, tcIdPredicate, vocab.lit(p.tcId.id)) +:
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
				???

			case instr: Instrument[T] =>
				???
		}
		getTriples(e).map(vocab.factory.tripleToStatement)
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
		(uri, meta.hasName, vocab.lit(s.name)) +: 
		Nil
	}
}
