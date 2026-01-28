package se.lu.nateko.cp.meta.metaflow

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS, XSD}
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value, ValueFactory}
import se.lu.nateko.cp.meta.core.data.{
	InstrumentDeployment as _,
	GeoFeature,
	UriResource,
	StationSpecifics,
	EtcStationSpecifics,
	AtcStationSpecifics,
	IcosStationSpecifics,
	IcosCitiesStationSpecifics,
	SitesStationSpecifics,
	NoStationSpecifics,
	Position,
	Organization
}
import se.lu.nateko.cp.meta.services.upload.StatementsProducer
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.*

class RdfMaker(vocab: CpVocab, val meta: CpmetaVocab)(using Envri) {

	private given factory: ValueFactory = vocab.factory
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
			(getIri(holder), meta.hasMembership, uri) +:
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
				s.core.location.toSeq.flatMap(positionTriples(_, uri)) ++:
				s.core.coverage.toSeq.flatMap(coverageTriples(_, uri)) ++:
				s.core.countryCode.map{ cc =>
					(uri, meta.countryCode, vocab.lit(cc.code))
				} ++:
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
				instr.comment.toSeq.map{comm =>
					(uri, RDFS.COMMENT, vocab.lit(comm))
				} ++:
				instr.owner.toSeq.map{owner =>
					(uri, meta.hasInstrumentOwner, getIri(owner))
				} ++:
				instr.vendor.toSeq.map{vendor =>
					(uri, meta.hasVendor, getIri(vendor))
				} ++:
				instr.partsCpIds.map{cpId =>
					(uri, meta.hasInstrumentComponent, vocab.getInstrument(cpId))
				} ++:
				instr.deployments.flatMap{depl =>
					val deplIri = getIri(depl)
					(uri, meta.ssn.hasDeployment, deplIri) +:
					getInstrDeploymentTriples(depl, deplIri)
				}

			case depl: InstrumentDeployment[T] => getInstrDeploymentTriples(depl, getIri(depl))
		}

		val tcIdPredicate: IRI = implicitly[TcConf[T]].tcIdPredicate(meta)

		val tcIdTriple = e.tcIdOpt.map{tcId => (uri, tcIdPredicate, vocab.lit(tcId.id))}
		(triples ++ tcIdTriple).map(factory.tripleToStatement)
	}

	def getIri[T <: TC](e: Entity[T]): IRI =  e match{

		case p: TcPerson[T] =>
			vocab.getPerson(p.cpId)

		case s: TcStation[T] => vocab.getStation(s.cpId)

		case ci: TcPlainOrg[T] =>
			vocab.getOrganization(ci.cpId)

		case instr: TcInstrument[T] =>
			vocab.getInstrument(instr.cpId)

		case depl: InstrumentDeployment[T] =>
			vocab.getInstrDeployment(depl.cpId)
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
			eco.networkNames.map{networkName =>
				(iri, meta.associatedNetwork, vocab.lit(networkName))
			} ++
			plainIcosStationSpecTriples(iri, eco)
		case atc: AtcStationSpecifics =>
			atc.wigosId.toSeq.map{wigosId =>
				(iri, meta.hasWigosId, vocab.lit(wigosId))
			} ++
			plainIcosStationSpecTriples(iri, atc)
		case icos: IcosStationSpecifics =>
			plainIcosStationSpecTriples(iri, icos)
		case cities: IcosCitiesStationSpecifics =>
			(iri, meta.belongsToNetwork, vocab.lit(cities.network.toString)) +:
			cities.timeZoneOffset.toSeq.map: tzoff =>
				(iri, meta.hasTimeZoneOffset, vocab.lit(tzoff))
		case other: (SitesStationSpecifics | NoStationSpecifics.type) => Seq.empty
	}

	private def getInstrDeploymentTriples[T <: TC](depl: InstrumentDeployment[T], deplIri: IRI): Seq[Triple] = {
		val stationIri: IRI = vocab.getStation(depl.stationUriId)

		(deplIri, RDF.TYPE, meta.ssn.deploymentClass) +:
		(deplIri, meta.atOrganization, stationIri) +:
		depl.variable.map{varName =>
			(deplIri, meta.hasVariableName, vocab.lit(varName))
		} ++:
		depl.pos.map{p =>
			(deplIri, meta.hasLatitude, vocab.lit(p.lat6, XSD.DOUBLE))
		} ++:
		depl.pos.map{p =>
			(deplIri, meta.hasLongitude, vocab.lit(p.lon6, XSD.DOUBLE))
		} ++:
		depl.pos.flatMap(_.alt).map{alt =>
			(deplIri, meta.hasSamplingHeight, vocab.lit(f"$alt%.2f", XSD.FLOAT))
		} ++:
		depl.start.map{start =>
			(deplIri, meta.hasStartTime, vocab.lit(start))
		} ++:
		depl.stop.map{stop =>
			(deplIri, meta.hasEndTime, vocab.lit(stop))
		} ++:
		depl.variable.flatMap(vocab.lookupIcosEcoDatasetVar).map{dsVarRes =>
			(deplIri, meta.ssn.forProperty, dsVarRes)
		}.toList
	}

	private def positionTriples(p: Position, iri: IRI): Seq[Triple] =
		(iri, meta.hasLatitude, vocab.lit(p.lat)) +:
		(iri, meta.hasLongitude, vocab.lit(p.lon)) +:
		p.alt.map{alt =>
			(iri, meta.hasElevation, vocab.lit(alt))
		}.toSeq

	private def coverageTriples(cov: GeoFeature, iri: IRI): Seq[Triple] =
		StatementsProducer(vocab, meta).getGeoFeatureStatements(iri, cov).collect:
			case Rdf4jStatement(s, p, o) => (s, p, o)

	private def plainIcosStationSpecTriples(iri: IRI, s: IcosStationSpecifics): Seq[Triple] = {
		s.stationClass.map{ stClass =>
			(iri, meta.hasStationClass, vocab.lit(stClass.toString))
		}.toSeq ++
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
