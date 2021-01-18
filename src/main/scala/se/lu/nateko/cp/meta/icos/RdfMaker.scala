package se.lu.nateko.cp.meta.icos

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS

import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.{data => core}
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j._
import java.time.Instant
import core.{Envri, UriResource}
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import se.lu.nateko.cp.meta.core.data.StationSpecifics
import se.lu.nateko.cp.meta.core.data.EtcStationSpecifics
import se.lu.nateko.cp.meta.core.data.IcosStationSpecifics
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.Position

class RdfMaker(vocab: CpVocab, val meta: CpmetaVocab) {

	private implicit val envri = Envri.ICOS
	private[this] implicit val factory = vocab.factory
	private type Triple = (IRI, IRI, Value)

	def createStatement(subj: Resource, pred: IRI, v: Value): Statement =
		factory.createStatement(subj, pred, v)

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
			memb.role.extra.map{einfo =>
				(uri, meta.hasExtraRoleInfo, vocab.lit(einfo))
			} ++:
			Nil
		}
		triples.map(factory.tripleToStatement)
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

			case s: TcStation[T] =>
				val stationClass = implicitly[TcConf[T]].stationClass(meta)
				(uri, RDF.TYPE, stationClass) +:
				(uri, meta.hasStationId, vocab.lit(s.core.id)) +:
				orgTriples(uri, s.core.org) ++:
				stationTriples(uri, s.core.specificInfo) ++:
				s.core.pictures.map{picUri =>
					(uri, meta.hasDepiction, vocab.lit(picUri.toString, XMLSchema.ANYURI))
				} ++:
				s.core.responsibleOrganization.map{respOrg =>
					(uri, meta.hasResponsibleOrganization, respOrg.self.uri.toRdf)
				} ++:
				coverageTriples(uri, s.core.coverage)

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
					(uri, meta.hasInstrumentComponent, vocab.getIcosInstrument(cpId))
				}
		}

		val tcIdPredicate: IRI = implicitly[TcConf[T]].tcIdPredicate(meta)

		val tcIdTriple = e.tcIdOpt.map{tcId => (uri, tcIdPredicate, vocab.lit(tcId.id))}
		(triples ++ tcIdTriple).map(factory.tripleToStatement)
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

	private def stationTriples(iri: IRI, s: StationSpecifics): Seq[Triple] =  s match{
		case eco: EtcStationSpecifics =>
			eco.climateZone.toSeq.map{ clzone =>
				(iri, meta.hasClimateZone, clzone.uri.toRdf)
			} ++
			eco.ecosystemType.map{ecoType =>
				(iri, meta.hasEcosystemType, ecoType.uri.toRdf)
			} ++
			eco.meanAnnualTemp.map{ meanTemp =>
				(iri, meta.hasMeanAnnualTemp, vocab.lit(meanTemp))
			} ++
			eco.meanAnnualPrecip.map{ meanPrecip =>
				(iri, meta.hasMeanAnnualPrecip, vocab.lit(meanPrecip))
			} ++
			eco.meanAnnualRad.map{ meanRad =>
				(iri, meta.hasMeanAnnualRadiation, vocab.lit(meanRad))
			} ++
			eco.stationPubs.map{stPub =>
				(iri, meta.hasAssociatedPublication, vocab.lit(stPub.toString, XMLSchema.ANYURI))
			} ++
			eco.stationDocs.map{stDoc =>
				(iri, meta.hasDocumentationUri, vocab.lit(stDoc.toString, XMLSchema.ANYURI))
			} ++
			plainIcosStationSpecTriples(iri, eco)
		case icos: IcosStationSpecifics =>
			plainIcosStationSpecTriples(iri, icos)
		case _ => Seq.empty
	}

	private def coverageTriples(iri: IRI, covOpt: Option[GeoFeature]): Seq[Triple] = covOpt match{
		case None => Seq.empty
		case Some(p: Position) =>
			(iri, meta.hasLatitude, vocab.lit(p.lat)) +:
			(iri, meta.hasLongitude, vocab.lit(p.lon)) +:
			p.alt.map{alt =>
				(iri, meta.hasElevation, vocab.lit(alt))
			}.toSeq
		case Some(cov) =>
			val spcovUri = factory.createIRI(iri.stringValue + "_spcov")
			(iri, meta.hasSpatialCoverage, spcovUri) ::
			(spcovUri, meta.asGeoJSON, vocab.lit(cov.geoJson)) ::
			Nil
	}

	private def plainIcosStationSpecTriples(iri: IRI, s: IcosStationSpecifics): Seq[Triple] = {
		s.stationClass.map{ stClass =>
			(iri, meta.hasStationClass, vocab.lit(stClass.toString))
		}.toSeq ++
		s.countryCode.map{ cc =>
			(iri, meta.countryCode, vocab.lit(cc.code))
		} ++
		s.labelingDate.map{ ldate =>
			(iri, meta.hasLabelingDate, vocab.lit(ldate.toString, XMLSchema.DATE))
		}
	}

	private def uriResourceTriples(iri: IRI, res: UriResource): Seq[Triple] = {
		res.label.map{ lbl =>
			(iri, RDFS.LABEL, vocab.lit(lbl))
		} ++:
		res.comments.map{ comm =>
			(iri, RDFS.COMMENT, vocab.lit(comm))
		}
	}

	private def orgTriples(iri: IRI, org: core.Organization): Seq[Triple] = {
		uriResourceTriples(iri, org.self) :+
		(iri, meta.hasName, vocab.lit(org.name)) :++
		org.email.map{ email =>
			(iri, meta.hasEmail, vocab.lit(email))
		} :++
		org.website.map{ website =>
			(iri, RDFS.SEEALSO, vocab.lit(website.toString, XMLSchema.ANYURI))
		}
	}
}
