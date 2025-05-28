package se.lu.nateko.cp.meta.services.upload

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS, XSD}
import org.eclipse.rdf4j.model.{IRI, Literal, Statement, Value, ValueFactory}
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{GeoFeature, GeoJson, LatLonBox, Position, flattenToSeq}
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab, UploadUserErrorException}
import se.lu.nateko.cp.meta.utils.*
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.{DataObjectDto, DataProductionDto, DocObjectDto, GeoCoverage, GeoJsonString, ObjectUploadDto, SpatioTemporalDto, StaticCollectionDto, StationTimeSeriesDto}

import java.net.URI
import java.time.Instant
import scala.collection.mutable.Buffer

class StatementsProducer(vocab: CpVocab, metaVocab: CpmetaVocab) {

	private given factory: ValueFactory = vocab.factory

	def getContribStatements(contribPredicate: IRI, contributors: Seq[URI], objectUri: IRI, makeSeqIri: => IRI)(using Envri): Iterable[Statement] =
		if contributors.length <= 1 then
			contributors.map: contributor =>
				makeSt(objectUri, contribPredicate, contributor.toRdf)
		else
			val contribsSeq = makeSeqIri
			Seq(
				makeSt(contribsSeq, RDF.TYPE, RDF.SEQ),
				makeSt(objectUri, contribPredicate, contribsSeq),
			) ++
			contributors.zipWithIndex.map: (contrib, index) =>
				val idxProp = factory.createIRI(RDF.NAMESPACE + "_" + (index + 1))
				makeSt(contribsSeq, idxProp, contrib.toRdf)
 
	//TODO Write a test for this, at least to control the number of statements to avoid accidental regressions
	def getObjStatements(meta: ObjectUploadDto, submittingOrg: URI)(using Envri, StatementSource): Seq[Statement] =
		import meta.hashSum
		import StatementSource.getValues

		val objectUri = vocab.getStaticObject(hashSum)

		val submissionUri = vocab.getSubmission(hashSum)
		val dct = metaVocab.dcterms

		val nextVersionStatements =
			val prevVersions = meta.isNextVersionOf.flattenToSeq
			if meta.partialUpload then
				prevVersions match
					case Seq() => Iterable.empty
					case Seq(v) =>
						val collIri = vocab.getNextVersionColl(v)
						val isTheOnlyNextVersion = getValues(collIri, dct.hasPart).filter(_ != objectUri).isEmpty
						val statements = Buffer(makeSt(collIri, dct.hasPart, objectUri))

						if isTheOnlyNextVersion then
							statements += makeSt(collIri, metaVocab.isNextVersionOf, vocab.getStaticObject(v))
							statements += makeSt(collIri, RDF.TYPE, metaVocab.plainCollectionClass)
						statements.toIndexedSeq
					case _ => throw UploadUserErrorException("Partial upload to multiple previous versions is not supported")
			else
				makeSt(objectUri, metaVocab.isNextVersionOf, prevVersions.map(vocab.getStaticObject))

		val specificStatements = meta match {
			case dobj: DataObjectDto => getDobjStatements(dobj)
			case doc: DocObjectDto => Seq(
				makeSt(objectUri, RDF.TYPE, metaVocab.docObjectClass)
			) ++
			makeSt(objectUri, metaVocab.dcterms.title, doc.title.map(vocab.lit)) ++
			makeSt(objectUri, metaVocab.dcterms.description, doc.description.map(vocab.lit)) ++
			getContribStatements(metaVocab.dcterms.creator, doc.authors, objectUri, vocab.getDocContribList(doc.hashSum))
		}
		val licUri: Option[URI] = meta.references.flatMap(_.licence).filterNot{
			_ === CitationMaker.defaultLicence.url
		}
		specificStatements ++ Seq(
			makeSt(objectUri, metaVocab.hasName, vocab.lit(meta.fileName)),
			makeSt(objectUri, metaVocab.hasSha256sum, vocab.lit(hashSum.base64, XSD.BASE64BINARY)),
			makeSt(objectUri, metaVocab.wasSubmittedBy, submissionUri),
			makeSt(submissionUri, RDF.TYPE, metaVocab.submissionClass),
			makeSt(submissionUri, metaVocab.prov.startedAtTime, vocab.lit(Instant.now)),
			makeSt(submissionUri, metaVocab.prov.wasAssociatedWith, submittingOrg.toRdf)
		) ++
			makeSt(objectUri, metaVocab.dcterms.license, licUri.map(_.toRdf)) ++
			nextVersionStatements ++
			makeSt(objectUri, metaVocab.hasDoi, meta.preExistingDoi.map(_.toString).map(vocab.lit))
	end getObjStatements

	private def getDobjStatements(meta: DataObjectDto)(using Envri): Seq[Statement] =
		import meta.hashSum

		val objectUri = vocab.getStaticObject(hashSum)

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
		makeSt(objectUri, metaVocab.hasKeywords, keywordsLit) ++ moratoriumStatements
	end getDobjStatements

	def getCollStatements(coll: StaticCollectionDto, collIri: IRI, submittingOrg: URI)(using Envri): Seq[Statement] = {
		val dct = metaVocab.dcterms
		Seq(
			makeSt(collIri, RDF.TYPE, metaVocab.collectionClass),
			makeSt(collIri, dct.title, vocab.lit(coll.title)),
			makeSt(collIri, dct.creator, submittingOrg.toRdf)
		) ++
			makeSt(collIri, dct.description, coll.description.map(vocab.lit)) ++
			makeSt(collIri, metaVocab.isNextVersionOf, coll.isNextVersionOf.flattenToSeq.map(vocab.getCollection)) ++
			makeSt(collIri, metaVocab.hasDoi, coll.preExistingDoi.map(_.toString).map(vocab.lit)) ++
			coll.coverage.toSeq.flatMap(getSpatialCoverageStatements(collIri, _)) ++
			coll.members.map{elem =>
				makeSt(collIri, dct.hasPart, elem.toRdf)
			} ++
			makeSt(collIri, RDFS.SEEALSO, coll.documentation.map(vocab.getStaticObject))
	}

	def getGeoFeatureStatements(itemIri: IRI, spatial: GeoFeature | GeoJsonString)(using Envri): Seq[Statement] =
		val gf: Option[GeoFeature] = spatial match
			case gf: GeoFeature => Some(gf)
			case _ => None

		inline def defaultCovUri = gf.flatMap(_.uri).fold(vocab.getSpatialCoverage(UriId(itemIri)))(_.toRdf)

		val geoJson: String = spatial match
			case feature: GeoFeature => GeoJson.fromFeature(feature).compactPrint
			case str: String => str

		val (covUri, specificStatements) = spatial match
			case p: Position => getPositionStatements(p)
			case LatLonBox(min, max, _, _) =>
				val covUri = defaultCovUri
				covUri -> Seq(
					makeSt(covUri, RDF.TYPE, metaVocab.latLonBoxClass),
					makeSt(covUri, metaVocab.hasNorthernBound, vocab.lit(max.lat)),
					makeSt(covUri, metaVocab.hasSouthernBound, vocab.lit(min.lat)),
					makeSt(covUri, metaVocab.hasWesternBound, vocab.lit(min.lon)),
					makeSt(covUri, metaVocab.hasEasternBound, vocab.lit(max.lon))
				)
			case _ =>
				val covUri = defaultCovUri
				covUri -> Seq(makeSt(covUri, RDF.TYPE, metaVocab.spatialCoverageClass))

		makeSt(itemIri, metaVocab.hasSpatialCoverage, covUri) +:
		makeSt(covUri, metaVocab.asGeoJSON, vocab.lit(geoJson)) +:
		makeSt(covUri, RDFS.LABEL, gf.flatMap(_.label).map(vocab.lit)) ++:
		specificStatements


	private def getPositionStatements(point: Position)(using Envri): (IRI, Seq[Statement]) =
		val posUri = point.uri.fold(vocab.getPosition(point))(_.toRdf)

		val statements = Seq(
			makeSt(posUri, metaVocab.hasLatitude, vocab.lit(point.lat6, XSD.DOUBLE)),
			makeSt(posUri, metaVocab.hasLongitude, vocab.lit(point.lon6, XSD.DOUBLE)),
			makeSt(posUri, RDF.TYPE, metaVocab.positionClass)
		) ++
		makeSt(posUri, metaVocab.hasElevation, point.alt.map(vocab.lit)) ++
		makeSt(posUri, RDFS.LABEL, point.label.map(vocab.lit))

		posUri -> statements


	private def getSpatioTemporalStatements(hash: Sha256Sum, meta: SpatioTemporalDto)(using Envri): Seq[Statement] = {
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
		getSpatialCoverageStatements(objUri, meta.spatial) ++
		makeSt(objUri, metaVocab.wasAcquiredBy, meta.forStation.orElse(meta.samplingHeight).map(_ => acq)) ++
		makeSt(acq, metaVocab.prov.wasAssociatedWith, meta.forStation.map(_.toRdf)) ++
		makeSt(acq, metaVocab.hasSamplingHeight, meta.samplingHeight.map(vocab.lit)) ++
		makeSt(objUri, RDFS.SEEALSO, meta.customLandingPage.map(_.toRdf)) ++
		meta.variables.toSeq.flatten.flatMap(getL3VarInfoStatements(objUri, hash, _))
	}

	private def getStationDataStatements(hash: Sha256Sum, meta: StationTimeSeriesDto)(using Envri): Seq[Statement] = {
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
		meta.samplingPoint.toSeq.flatMap{pos =>
			val (samplUri, posStatements) = getPositionStatements(pos)
			makeSt(aquisitionUri, metaVocab.hasSamplingPoint, samplUri) +:
			posStatements
		} ++
		makeSt(aquisitionUri, metaVocab.hasSamplingHeight, meta.samplingHeight.map(vocab.lit)) ++
		meta.instruments.map(instr => makeSt(aquisitionUri, metaVocab.wasPerformedWith, instr.toRdf)) ++
		meta.production.map(getProductionStatements(hash, _)).getOrElse(Seq.empty) ++
		meta.spatial.toSeq.flatMap(getSpatialCoverageStatements(objectUri, _))
	}

	private def getProductionStatements(hash: Sha256Sum, prod: DataProductionDto)(using Envri): Seq[Statement] = {
		val productionUri = vocab.getProduction(hash)

		val contribStatements = getContribStatements(
			metaVocab.wasParticipatedInBy, prod.contributors, productionUri, vocab.getProductionContribList(hash)
		)

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
		contribStatements ++
		prod.sources.getOrElse(Nil).map{srcHash =>
			val src = vocab.getStaticObject(srcHash)
			makeSt(objectUri, metaVocab.prov.hadPrimarySource, src)
		}
	}

	private def getSpatialCoverageStatements(itemIri: IRI, spatial: GeoCoverage)(using Envri): Seq[Statement] =
		spatial match
			case feature: GeoFeature => getGeoFeatureStatements(itemIri, feature)
			case str: GeoJsonString @unchecked => getGeoFeatureStatements(itemIri, str)
			case covUri: URI =>
				Seq(makeSt(itemIri, metaVocab.hasSpatialCoverage, covUri.toRdf))


	private def getL3VarInfoStatements(objIri: IRI, hash: Sha256Sum, varName: String)(using Envri): Seq[Statement] = {
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
