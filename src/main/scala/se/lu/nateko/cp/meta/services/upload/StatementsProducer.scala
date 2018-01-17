package se.lu.nateko.cp.meta.services.upload

import java.net.URI
import java.time.Instant

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XMLSchema

import se.lu.nateko.cp.meta.DataProductionDto
import se.lu.nateko.cp.meta.ElaboratedProductMetadata
import se.lu.nateko.cp.meta.StationDataMetadata
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.LatLonBox
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.StaticCollectionDto

class StatementsProducer(vocab: CpVocab, metaVocab: CpmetaVocab) {

	private implicit val factory = vocab.factory

	def getStatements(meta: UploadMetadataDto, submittingOrg: URI): Seq[Statement] = {
		import meta.{ hashSum, objectSpecification }

		val objectUri = vocab.getDataObject(hashSum)
		val submissionUri = vocab.getSubmission(hashSum)

		val specificStatements = meta.specificInfo.fold(
			elProd => getElaboratedProductStatements(hashSum, elProd),
			stationData => getStationDataStatements(hashSum, stationData)
		)

		specificStatements ++ Seq(
			makeSt(objectUri, metaVocab.hasName, vocab.lit(meta.fileName)),
			makeSt(objectUri, RDF.TYPE, metaVocab.dataObjectClass),
			makeSt(objectUri, metaVocab.hasSha256sum, vocab.lit(hashSum.base64, XMLSchema.BASE64BINARY)),
			makeSt(objectUri, metaVocab.hasObjectSpec, objectSpecification.toRdf),
			makeSt(objectUri, metaVocab.wasSubmittedBy, submissionUri),
			makeSt(submissionUri, RDF.TYPE, metaVocab.submissionClass),
			makeSt(submissionUri, metaVocab.prov.startedAtTime, vocab.lit(Instant.now)),
			makeSt(submissionUri, metaVocab.prov.wasAssociatedWith, submittingOrg.toRdf)
		) ++
			makeSt(objectUri, metaVocab.isNextVersionOf, meta.isNextVersionOf.map(vocab.getDataObject))
	}

	def getCollStatements(coll: StaticCollectionDto, collIri: IRI, submittingOrg: URI): Seq[Statement] = {
		val dct = metaVocab.dcterms
		Seq(
			makeSt(collIri, dct.title, vocab.lit(coll.title)),
			makeSt(collIri, dct.creator, factory.createIRI(submittingOrg))
		) ++
			makeSt(collIri, dct.description, coll.description.map(vocab.lit)) ++
			makeSt(collIri, metaVocab.isNextVersionOf, coll.isNextVersionOf.map(vocab.getCollection)) ++
			coll.members.map{elem =>
				makeSt(collIri, dct.hasPart, vocab.getDataObject(elem))
			}
	}

	def getGeoFeatureStatements(hash: Sha256Sum, spatial: GeoFeature): Seq[Statement] = {
		val objectUri = vocab.getDataObject(hash)
		val covUri = vocab.getSpatialCoverate(hash)

		makeSt(objectUri, metaVocab.hasSpatialCoverage, covUri) +:
		makeSt(covUri, metaVocab.asGeoJSON, vocab.lit(spatial.geoJson)) +:
		(spatial match{
			case LatLonBox(min, max, labelOpt) =>
				Seq(
					makeSt(covUri, RDF.TYPE, metaVocab.latLonBoxClass),
					makeSt(covUri, metaVocab.hasNothernBound, vocab.lit(max.lat)),
					makeSt(covUri, metaVocab.hasSouthernBound, vocab.lit(min.lat)),
					makeSt(covUri, metaVocab.hasWesternBound, vocab.lit(min.lon)),
					makeSt(covUri, metaVocab.hasEasternBound, vocab.lit(max.lon))
				) ++
				makeSt(covUri, RDFS.LABEL, labelOpt.map(vocab.lit))
			case _ =>
				Seq(makeSt(covUri, RDF.TYPE, metaVocab.spatialCoverageClass))
		})
	}

	private def getElaboratedProductStatements(hash: Sha256Sum, meta: ElaboratedProductMetadata): Seq[Statement] = {
		val objUri = vocab.getDataObject(hash)
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
		makeSt(objUri, RDFS.SEEALSO, meta.customLandingPage.map(uri => vocab.factory.createIRI(uri)))
	}

	private def getStationDataStatements(hash: Sha256Sum, meta: StationDataMetadata): Seq[Statement] = {
		val objectUri = vocab.getDataObject(hash)
		val aquisitionUri = vocab.getAcquisition(hash)
		val acqStart = meta.acquisitionInterval.map(_.start)
		val acqStop = meta.acquisitionInterval.map(_.stop)

		Seq(
			makeSt(objectUri, metaVocab.wasAcquiredBy, aquisitionUri),
			makeSt(aquisitionUri, RDF.TYPE, metaVocab.aquisitionClass),
			makeSt(aquisitionUri, metaVocab.prov.wasAssociatedWith, meta.station.toRdf)
		) ++
		makeSt(objectUri, metaVocab.hasNumberOfRows, meta.nRows.map(vocab.lit)) ++
		makeSt(aquisitionUri, metaVocab.prov.startedAtTime, acqStart.map(vocab.lit)) ++
		makeSt(aquisitionUri, metaVocab.prov.endedAtTime, acqStop.map(vocab.lit)) ++
		makeSt(aquisitionUri, metaVocab.wasPerformedWith, meta.instrument.map(_.toRdf)) ++
		makeSt(aquisitionUri, metaVocab.hasSamplingHeight, meta.samplingHeight.map(vocab.lit)) ++
		meta.production.map(getProductionStatements(hash, _)).getOrElse(Seq.empty)
	}

	private def getProductionStatements(hash: Sha256Sum, prod: DataProductionDto): Seq[Statement] = {
		val productionUri = vocab.getProduction(hash)
		val objectUri = vocab.getDataObject(hash)
		Seq(
			makeSt(objectUri, metaVocab.wasProducedBy, productionUri),
			makeSt(productionUri, RDF.TYPE, metaVocab.productionClass),
			makeSt(productionUri, metaVocab.wasPerformedBy, prod.creator.toRdf),
			makeSt(productionUri, metaVocab.hasEndTime, vocab.lit(prod.creationDate))
		) ++
		makeSt(productionUri, RDFS.COMMENT, prod.comment.map(vocab.lit)) ++
		makeSt(productionUri, metaVocab.wasHostedBy, prod.hostOrganization.map(_.toRdf)) ++
		prod.contributors.map{ contrib =>
			makeSt(productionUri, metaVocab.wasParticipatedInBy, contrib.toRdf)
		}
	}

	private def getSpatialCoverageStatements(hash: Sha256Sum, spatial: Either[GeoFeature, URI]): Seq[Statement] = {
		spatial match{
			case Left(feature) =>
				getGeoFeatureStatements(hash, feature)
			case Right(existing) =>
				// TODO Add a validation that 'existing' actually exists
				/* TODO Protect 'existing' coverage object in the metadata update scenario
				 *  (otherwise, 'existing' may be removed, if it is in the same RDF graph and not used
				 *  by this object any more; it may be needed by others)
				 */
				val objectUri = vocab.getDataObject(hash)
				Seq(makeSt(objectUri, metaVocab.hasSpatialCoverage, existing.toRdf))
		}
	}

	private def makeSt(subj: IRI, pred: IRI, obj: Option[Value]): Iterable[Statement] =
		obj.map(factory.createStatement(subj, pred, _))

	private def makeSt(subj: IRI, pred: IRI, obj: Value): Statement = factory.createStatement(subj, pred, obj)

}