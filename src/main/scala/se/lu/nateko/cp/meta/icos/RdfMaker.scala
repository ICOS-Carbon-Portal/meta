package se.lu.nateko.cp.meta.icos

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS

import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.EnrichedValueFactory
import java.time.Instant

class RdfMaker(vocab: CpVocab, val meta: CpmetaVocab) {

	private implicit val envri = Envri.ICOS

	def createStatement(subj: Resource, pred: IRI, v: Value): Statement =
		vocab.factory.createStatement(subj, pred, v)

	def getStatements[T <: TC : TcConf](memb: Membership[T]): Seq[Statement] = {
		val uri = vocab.getMembership(memb.cpId)
		val holder = memb.role.holder
		val role = memb.role.kind
		val org = memb.role.org
		val label = s"${holder.lname} as ${role.name} at ${org.cpId}"

		val triples: Seq[(IRI, IRI, Value)] = {
			(uri, RDF.TYPE, meta.membershipClass) +:
			(uri, RDFS.LABEL, vocab.lit(label)) +:
			(uri, meta.atOrganization, getIri(org)) +:
			(uri, meta.hasRole, vocab.getRole(role)) +:
			(getIri(memb.role.holder), meta.hasMembership, uri) +:
			memb.start.map{inst =>
				(uri, meta.hasStartTime, vocab.lit(inst))
			} ++:
			memb.stop.map{inst =>
				(uri, meta.hasEndTime, vocab.lit(inst))
			} ++:
			memb.role.weight.map{weight =>
				(uri, meta.hasAttributionWeight, vocab.lit(weight))
			} ++:
			Nil
		}
		triples.map(vocab.factory.tripleToStatement)
	}

	def getMembershipEnd(membId: UriId): Statement = {
		val uri = vocab.getMembership(membId)
		createStatement(uri, meta.hasEndTime, vocab.lit(Instant.now))
	}

	def getStatements[T <: TC : TcConf](e: Entity[T]): Seq[Statement] = {

		val uri = getIri(e)

		val triples: Seq[(IRI, IRI, Value)] = e match{

			case p: Person[T] =>
				(uri, RDF.TYPE, meta.personClass) +:
				(uri, meta.hasFirstName, vocab.lit(p.fname)) +:
				(uri, meta.hasLastName, vocab.lit(p.lname)) +:
				p.email.map{email =>
					(uri, meta.hasEmail, vocab.lit(email))
				} ++:
				p.orcid.map{orcid =>
					(uri, meta.hasOrcidId, vocab.lit(orcid.id))
				}.toList

			case s: TcStationaryStation[T] =>
				(uri, meta.hasLatitude, vocab.lit(s.pos.lat)) +:
				(uri, meta.hasLongitude, vocab.lit(s.pos.lon)) +:
				s.pos.alt.map{alt =>
					(uri, meta.hasElevation, vocab.lit(alt))
				} ++:
				stationTriples(s)

			case s: TcMobileStation[T] =>
				stationTriples(s) ++ s.geoJson.toList.flatMap{json =>
					val spcovUri = vocab.factory.createIRI(uri.stringValue + "_spcov")
					(uri, meta.hasSpatialCoverage, spcovUri) ::
					(spcovUri, meta.asGeoJSON, vocab.lit(json)) ::
					Nil
				}


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

		val tcIdTriple = e.tcIdOpt.map{tcId => (uri, tcIdPredicate, vocab.lit(tcId.id))}
		(triples ++ tcIdTriple).map(vocab.factory.tripleToStatement)
	}

	def getIri[T <: TC : TcConf](e: Entity[T]): IRI =  e match{

		case p: Person[T] =>
			vocab.getPerson(p.cpId)

		case s: TcStation[T] => vocab.getIcosLikeStation(s.cpId)

		case ci: CompanyOrInstitution[T] =>
			vocab.getOrganization(ci.cpId)

		case instr: Instrument[T] =>
			vocab.getIcosInstrument(instr.cpId)
	}

	private def stationTriples[T <: TC : TcConf](s: TcStation[T]): List[(IRI, IRI, Value)] = {
		val uri = getIri(s)
		val stationClass = implicitly[TcConf[T]].stationClass(meta)
		(uri, RDF.TYPE, stationClass) ::
		(uri, meta.hasStationId, vocab.lit(s.id)) ::
		(uri, meta.hasName, vocab.lit(s.name)) ::
		s.country.toList.map{cc =>
			(uri, meta.countryCode, vocab.lit(cc.code))
		}
	}
}
