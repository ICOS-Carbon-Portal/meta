package se.lu.nateko.cp.meta.icos

import java.time.Instant

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XMLSchema

import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j._

class RdfMaker(vocab: CpVocab, val meta: CpmetaVocab) {

	private implicit val envri = Envri.ICOS
	private[this] implicit val factory = vocab.factory
	private type Triple = (IRI, IRI, Value)

	def createStatement(subj: Resource, pred: IRI, v: Value): Statement =
		factory.createStatement(subj, pred, v)

	def getStatements[T <: TC](memb: Membership[T]): Seq[Statement] = {
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

	private def fundingTriples[T <: TC](fund: TcFunding[T]): Seq[(IRI, IRI, Value)] = {
		val iri = vocab.getFunding(fund.cpId)
		(iri, RDF.TYPE, meta.fundingClass) +:
		(iri, meta.hasFunder, getIri(fund.funder)) +:
		fund.core.awardNumber.map{anum =>
			(iri, meta.awardNumber, vocab.lit(anum))
		} ++:
		fund.core.awardTitle.map{atit =>
			(iri, meta.awardTitle, vocab.lit(atit))
		} ++:
		fund.core.awardUrl.map{auri =>
			(iri, meta.awardURI, vocab.lit(auri))
		} ++:
		fund.core.start.map{startD => 
			(iri, meta.hasStartDate, vocab.lit(startD))
		} ++:
		fund.core.stop.map{endD => 
			(iri, meta.hasEndDate, vocab.lit(endD))
		} ++:
		uriResourceTriples(iri, fund.core.self) ++:
		Nil
	}

	def getStatements[T <: TC : TcConf](e: Entity[T]): Seq[Statement] = {

		val uri = getIri(e)

		val triples: Seq[(IRI, IRI, Value)] = e match{

			case p: TcPerson[T] =>
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
					(uri, meta.hasDepiction, vocab.lit(picUri))
				} ++:
				s.responsibleOrg.map{respOrg =>
					(uri, meta.hasResponsibleOrganization, getIri(respOrg))
				} ++:
				coverageTriples(uri, s.core.coverage) ++:
				s.funding.flatMap{fund =>
					(uri, meta.hasFunding, vocab.getFunding(fund.cpId)) +:
					fundingTriples(fund)
				}

			case go: TcGenericOrg[T] =>
				(uri, RDF.TYPE, meta.orgClass) +:
				orgTriples(uri, go.org)

			case fu: TcFunder[T] =>
				(uri, RDF.TYPE, meta.funderClass) +:
				fu.core.id.toSeq.flatMap{
					case (id, idType) => Seq(
						(uri, meta.funderIdentifier, vocab.lit(id)),
						(uri, meta.funderIdentifierType, vocab.lit(idType.toString))
					)
				} ++:
				orgTriples(uri, fu.org)

			case instr: TcInstrument[T] =>
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

	def getIri[T <: TC](e: Entity[T]): IRI =  e match{

		case p: TcPerson[T] =>
			vocab.getPerson(p.cpId)

		case s: TcStation[T] => vocab.getIcosLikeStation(s.cpId)

		case ci: TcPlainOrg[T] =>
			vocab.getOrganization(ci.cpId)

		case instr: TcInstrument[T] =>
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
				(iri, meta.hasAssociatedPublication, vocab.lit(stPub))
			} ++
			eco.stationDocs.map{stDoc =>
				(iri, meta.hasDocumentationUri, vocab.lit(stDoc))
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
		case Some(box: LatLonBox) =>
			val spcovUri = box.uri.map(_.toRdf).getOrElse(vocab.getSpatialCoverage(UriId(iri)))
			(iri, meta.hasSpatialCoverage, spcovUri) ::
			(spcovUri, RDF.TYPE, meta.latLonBoxClass) ::
			(spcovUri, meta.hasNorthernBound, vocab.lit(box.max.lat)) ::
			(spcovUri, meta.hasEasternBound, vocab.lit(box.max.lon)) ::
			(spcovUri, meta.hasSouthernBound, vocab.lit(box.min.lat)) ::
			(spcovUri, meta.hasWesternBound, vocab.lit(box.min.lon)) ::
			box.label.toList.map{lbl =>
				(spcovUri, RDFS.LABEL, vocab.lit(lbl))
			}
		case Some(cov) =>
			val spcovUri = vocab.getSpatialCoverage(UriId(iri))
			(iri, meta.hasSpatialCoverage, spcovUri) ::
			(spcovUri, RDF.TYPE, meta.spatialCoverageClass) ::
			(spcovUri, meta.asGeoJSON, vocab.lit(GeoJson.fromFeature(cov).compactPrint)) ::
			cov.label.toList.map{lbl =>
				(spcovUri, RDFS.LABEL, vocab.lit(lbl))
			}
	}

	private def plainIcosStationSpecTriples(iri: IRI, s: IcosStationSpecifics): Seq[Triple] = {
		s.stationClass.map{ stClass =>
			(iri, meta.hasStationClass, vocab.lit(stClass.toString))
		}.toSeq ++
		s.countryCode.map{ cc =>
			(iri, meta.countryCode, vocab.lit(cc.code))
		} ++
		s.timeZoneOffset.map{tz =>
			(iri, meta.hasTimeZoneOffset, vocab.lit(tz))
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

	private def orgTriples(iri: IRI, org: Organization): Seq[Triple] = {
		uriResourceTriples(iri, org.self) :+
		(iri, meta.hasName, vocab.lit(org.name)) :++
		org.email.map{ email =>
			(iri, meta.hasEmail, vocab.lit(email))
		} :++
		org.website.map{ website =>
			(iri, RDFS.SEEALSO, website.toRdf)
		}
	}
}
