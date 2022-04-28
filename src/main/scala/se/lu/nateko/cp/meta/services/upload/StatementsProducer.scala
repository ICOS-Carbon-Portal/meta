package se.lu.nateko.cp.meta.services.upload

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.DataProductionDto
import se.lu.nateko.cp.meta.DocObjectDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.SpatioTemporalDto
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.StationTimeSeriesDto
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.GeoJson
import se.lu.nateko.cp.meta.core.data.LatLonBox
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import se.lu.nateko.cp.meta.utils.*
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import java.time.Instant

class StatementsProducer(vocab: CpVocab, metaVocab: CpmetaVocab) {

	private given factory: ValueFactory = vocab.factory

	//TODO Write a test for this, at least to control the number of statements to avoid accidental regressions
	def getObjStatements(meta: ObjectUploadDto, submittingOrg: URI)(implicit envri: Envri): Seq[Statement] = {
		import meta.hashSum

		val objectUri = vocab.getStaticObject(hashSum)
		val submissionUri = vocab.getSubmission(hashSum)

		val specificStatements = meta match {
			case dobj: DataObjectDto => getDobjStatements(dobj)
			case doc: DocObjectDto => Seq(
				makeSt(objectUri, RDF.TYPE, metaVocab.docObjectClass)
			) ++
			makeSt(objectUri, metaVocab.dcterms.title, doc.title.map(vocab.lit)) ++
			makeSt(objectUri, metaVocab.dcterms.description, doc.description.map(vocab.lit)) ++
			doc.authors.map{ author =>
				makeSt(objectUri, metaVocab.dcterms.creator, author.toRdf)
			}
		}

		specificStatements ++ Seq(
			makeSt(objectUri, metaVocab.hasName, vocab.lit(meta.fileName)),
			makeSt(objectUri, metaVocab.hasSha256sum, vocab.lit(hashSum.base64, XMLSchema.BASE64BINARY)),
			makeSt(objectUri, metaVocab.wasSubmittedBy, submissionUri),
			makeSt(submissionUri, RDF.TYPE, metaVocab.submissionClass),
			makeSt(submissionUri, metaVocab.prov.startedAtTime, vocab.lit(Instant.now)),
			makeSt(submissionUri, metaVocab.prov.wasAssociatedWith, submittingOrg.toRdf)
		) ++
			makeSt(objectUri, metaVocab.isNextVersionOf, meta.isNextVersionOf.flattenToSeq.map(vocab.getStaticObject)) ++
			makeSt(objectUri, metaVocab.hasDoi, meta.preExistingDoi.map(_.toString).map(vocab.lit))
	}

	private def getDobjStatements(meta: DataObjectDto)(implicit envri: Envri): Seq[Statement] = {
		import meta.hashSum

		val objectUri = vocab.getStaticObject(hashSum)
		val licUri: Option[URI] = meta.references.flatMap(_.licence).filter{uri =>
			!CitationMaker.defaultLicences.get(envri).map(_.url).contains(uri)
		}

		val specificStatements = meta.specificInfo.fold(
			elProd => getSpatioTemporalStatements(hashSum, elProd),
			stationData => getStationDataStatements(hashSum, stationData)
		)

		val keywordsLit: Option[Literal] = for(
			refs <- meta.references;
			rawKeywords <- refs.keywords;
			keywords = rawKeywords.map(_.trim).filterNot(_.isEmpty);
			if !keywords.isEmpty
		) yield vocab.lit(keywords.mkString(","))

		val moratoriumStatements = meta.references.flatMap(_.moratorium)
			.filter(_.compareTo(Instant.now()) > 0).map{moratorium =>
				val submissionUri = vocab.getSubmission(hashSum)
				makeSt(submissionUri, metaVocab.prov.endedAtTime, vocab.lit(moratorium))
			}

		specificStatements ++ Seq(
			makeSt(objectUri, RDF.TYPE, metaVocab.dataObjectClass),
			makeSt(objectUri, metaVocab.hasObjectSpec, meta.objectSpecification.toRdf),
		) ++
		makeSt(objectUri, metaVocab.hasKeywords, keywordsLit) ++
		makeSt(objectUri, metaVocab.dcterms.license, licUri.map(_.toRdf)) ++
		moratoriumStatements
	}

	def getCollStatements(coll: StaticCollectionDto, collIri: IRI, submittingOrg: URI)(implicit envri: Envri): Seq[Statement] = {
		val dct = metaVocab.dcterms
		Seq(
			makeSt(collIri, RDF.TYPE, metaVocab.collectionClass),
			makeSt(collIri, dct.title, vocab.lit(coll.title)),
			makeSt(collIri, dct.creator, submittingOrg.toRdf)
		) ++
			makeSt(collIri, dct.description, coll.description.map(vocab.lit)) ++
			makeSt(collIri, metaVocab.isNextVersionOf, coll.isNextVersionOf.flattenToSeq.map(vocab.getCollection)) ++
			makeSt(collIri, metaVocab.hasDoi, coll.preExistingDoi.map(_.toString).map(vocab.lit)) ++
			coll.members.map{elem =>
				makeSt(collIri, dct.hasPart, elem.toRdf)
			}
	}

	def getGeoFeatureStatements(hash: Sha256Sum, spatial: GeoFeature)(implicit envri: Envri): Seq[Statement] = {
		val objectUri = vocab.getStaticObject(hash)
		val covUri = vocab.getSpatialCoverage(hash)

		makeSt(objectUri, metaVocab.hasSpatialCoverage, covUri) +:
		makeSt(covUri, metaVocab.asGeoJSON, vocab.lit(GeoJson.fromFeature(spatial).compactPrint)) +:
		makeSt(covUri, RDFS.LABEL, spatial.label.map(vocab.lit)) ++:
		(spatial match{
			case LatLonBox(min, max, _, _) =>
				Seq(
					makeSt(covUri, RDF.TYPE, metaVocab.latLonBoxClass),
					makeSt(covUri, metaVocab.hasNorthernBound, vocab.lit(max.lat)),
					makeSt(covUri, metaVocab.hasSouthernBound, vocab.lit(min.lat)),
					makeSt(covUri, metaVocab.hasWesternBound, vocab.lit(min.lon)),
					makeSt(covUri, metaVocab.hasEasternBound, vocab.lit(max.lon))
				)

			case _ =>
				Seq(makeSt(covUri, RDF.TYPE, metaVocab.spatialCoverageClass))
		})
	}

	private def getPositionStatements(aquisitionUri: IRI, point: Position)(implicit envri: Envri): Seq[Statement] = {
		val samplUri = vocab.getPosition(point)

		Seq(
			makeSt(aquisitionUri, metaVocab.hasSamplingPoint, samplUri),
			makeSt(samplUri, metaVocab.hasLatitude, vocab.lit(point.lat6, XMLSchema.DOUBLE)),
			makeSt(samplUri, metaVocab.hasLongitude, vocab.lit(point.lon6, XMLSchema.DOUBLE)),
			makeSt(samplUri, RDF.TYPE, metaVocab.positionClass)
		) ++
		makeSt(samplUri, metaVocab.hasElevation, point.alt.map(vocab.lit)) ++
		makeSt(samplUri, RDFS.LABEL, point.label.map(vocab.lit))
	}

	private def getSpatioTemporalStatements(hash: Sha256Sum, meta: SpatioTemporalDto)(implicit envri: Envri): Seq[Statement] = {
		val objUri = vocab.getStaticObject(hash)
		val acq = vocab.getAcquisition(hash)
		Seq(
			makeSt(objUri, metaVocab.dcterms.title, vocab.lit(meta.title)),
			makeSt(objUri, metaVocab.hasStartTime, vocab.lit(meta.temporal.interval.start)),
			makeSt(objUri, metaVocab.hasEndTime, vocab.lit(meta.temporal.interval.stop))
		) ++
		meta.temporal.resolution.map{tempResol =>
			makeSt(objUri, metaVocab.hasTemporalResolution, vocab.lit(tempResol))
		} ++
		makeSt(objUri, metaVocab.dcterms.description, meta.description.map(vocab.lit)) ++
		getProductionStatements(hash, meta.production) ++
		getSpatialCoverageStatements(hash, meta.spatial) ++
		makeSt(objUri, metaVocab.wasAcquiredBy, meta.forStation.orElse(meta.samplingHeight).map(_ => acq)) ++
		makeSt(acq, metaVocab.prov.wasAssociatedWith, meta.forStation.map(_.toRdf)) ++
		makeSt(acq, metaVocab.hasSamplingHeight, meta.samplingHeight.map(vocab.lit)) ++
		makeSt(objUri, RDFS.SEEALSO, meta.customLandingPage.map(_.toRdf)) ++
		meta.variables.toSeq.flatten.flatMap(getL3VarInfoStatements(objUri, hash, _))
	}

	private def getStationDataStatements(hash: Sha256Sum, meta: StationTimeSeriesDto)(implicit envri: Envri): Seq[Statement] = {
		val objectUri = vocab.getStaticObject(hash)
		val aquisitionUri = vocab.getAcquisition(hash)
		val acqStart = meta.acquisitionInterval.map(_.start)
		val acqStop = meta.acquisitionInterval.map(_.stop)

		Seq(
			makeSt(objectUri, metaVocab.wasAcquiredBy, aquisitionUri),
			makeSt(aquisitionUri, RDF.TYPE, metaVocab.aquisitionClass),
			makeSt(aquisitionUri, metaVocab.prov.wasAssociatedWith, meta.station.toRdf)
		) ++
		makeSt(aquisitionUri, metaVocab.wasPerformedAt, meta.site.map(_.toRdf)) ++
		makeSt(objectUri, metaVocab.hasNumberOfRows, meta.nRows.map(vocab.lit)) ++
		makeSt(aquisitionUri, metaVocab.prov.startedAtTime, acqStart.map(vocab.lit)) ++
		makeSt(aquisitionUri, metaVocab.prov.endedAtTime, acqStop.map(vocab.lit)) ++
		meta.samplingPoint.map(getPositionStatements(aquisitionUri, _)).getOrElse(Seq.empty)++
		makeSt(aquisitionUri, metaVocab.hasSamplingHeight, meta.samplingHeight.map(vocab.lit)) ++
		meta.instruments.map(instr => makeSt(aquisitionUri, metaVocab.wasPerformedWith, instr.toRdf)) ++
		meta.production.map(getProductionStatements(hash, _)).getOrElse(Seq.empty)
	}

	private def getProductionStatements(hash: Sha256Sum, prod: DataProductionDto)(implicit envri: Envri): Seq[Statement] = {
		val productionUri = vocab.getProduction(hash)
		val objectUri = vocab.getStaticObject(hash)
		Seq(
			makeSt(objectUri, metaVocab.wasProducedBy, productionUri),
			makeSt(productionUri, RDF.TYPE, metaVocab.productionClass),
			makeSt(productionUri, metaVocab.wasPerformedBy, prod.creator.toRdf),
			makeSt(productionUri, metaVocab.hasEndTime, vocab.lit(prod.creationDate))
		) ++
		makeSt(productionUri, RDFS.COMMENT, prod.comment.map(vocab.lit)) ++
		makeSt(productionUri, RDFS.SEEALSO, prod.documentation.map(vocab.getStaticObject)) ++
		makeSt(productionUri, metaVocab.wasHostedBy, prod.hostOrganization.map(_.toRdf)) ++
		prod.contributors.map{ contrib =>
			makeSt(productionUri, metaVocab.wasParticipatedInBy, contrib.toRdf)
		} ++
		prod.sources.getOrElse(Nil).map{srcHash =>
			val src = vocab.getStaticObject(srcHash)
			makeSt(objectUri, metaVocab.prov.hadPrimarySource, src)
		}
	}

	private def getSpatialCoverageStatements(hash: Sha256Sum, spatial: Either[GeoFeature, URI])(implicit envri: Envri): Seq[Statement] = {
		spatial match{
			case Left(feature) =>
				getGeoFeatureStatements(hash, feature)
			case Right(existing) =>
				// TODO Add a validation that 'existing' actually exists
				/* TODO Protect 'existing' coverage object in the metadata update scenario
				 *  (otherwise, 'existing' may be removed, if it is in the same RDF graph and not used
				 *  by this object any more; it may be needed by others)
				 */
				val objectUri = vocab.getStaticObject(hash)
				Seq(makeSt(objectUri, metaVocab.hasSpatialCoverage, existing.toRdf))
		}
	}

	private def getL3VarInfoStatements(objIri: IRI, hash: Sha256Sum, varName: String)(implicit envri: Envri): Seq[Statement] = {
		val vUri = vocab.getVarInfo(hash, varName)
		Seq(
			makeSt(objIri, metaVocab.hasActualVariable, vUri),
			makeSt(vUri, RDF.TYPE, metaVocab.variableInfoClass),
			makeSt(vUri, RDFS.LABEL, vocab.lit(varName))
		)
	}

	private def makeSt(subj: IRI, pred: IRI, obj: Iterable[Value]): Iterable[Statement] =
		obj.map(factory.createStatement(subj, pred, _))

	private def makeSt(subj: IRI, pred: IRI, obj: Value): Statement = factory.createStatement(subj, pred, obj)

}
