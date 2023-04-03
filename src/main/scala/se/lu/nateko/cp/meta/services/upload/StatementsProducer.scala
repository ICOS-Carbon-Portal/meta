package se.lu.nateko.cp.meta.services.upload

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.DataProductionDto
import se.lu.nateko.cp.meta.DocObjectDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.SpatioTemporalDto
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.StationTimeSeriesDto
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri
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
	def getObjStatements(meta: ObjectUploadDto, submittingOrg: URI)(using Envri): Seq[Statement] = {
		import meta.hashSum

		val objectUri = vocab.getStaticObject(hashSum)

		val submissionUri = vocab.getSubmission(hashSum)
		val dct = metaVocab.dcterms

		val nextVersionStatements =
			if meta.nextVersionIsPartial then
				meta.isNextVersionOf match
					case Some(Left(v)) =>
						val collIri = vocab.getNextVersionColl(v)

						Iterable(
							makeSt(collIri, metaVocab.isNextVersionOf, vocab.getStaticObject(v)),
							makeSt(collIri, RDF.TYPE, metaVocab.plainCollectionClass),
							makeSt(collIri, dct.hasPart, objectUri)
						)
					case _ => Iterable.empty
			else
				makeSt(objectUri, metaVocab.isNextVersionOf, meta.isNextVersionOf.flattenToSeq.map(vocab.getStaticObject))

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
	}

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
			coll.members.map{elem =>
				makeSt(collIri, dct.hasPart, elem.toRdf)
			}
	}

	def getGeoFeatureStatements(hash: Sha256Sum, spatial: GeoFeature)(using Envri): Seq[Statement] =
		val objectUri = vocab.getStaticObject(hash)
		inline def defaultCovUri = spatial.uri.fold(vocab.getSpatialCoverage(hash))(_.toRdf)

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

		makeSt(objectUri, metaVocab.hasSpatialCoverage, covUri) +:
		makeSt(covUri, metaVocab.asGeoJSON, vocab.lit(GeoJson.fromFeature(spatial).compactPrint)) +:
		makeSt(covUri, RDFS.LABEL, spatial.label.map(vocab.lit)) ++:
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
		getSpatialCoverageStatements(hash, meta.spatial) ++
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
		meta.production.map(getProductionStatements(hash, _)).getOrElse(Seq.empty)
	}

	private def getProductionStatements(hash: Sha256Sum, prod: DataProductionDto)(using Envri): Seq[Statement] = {
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

	private def getSpatialCoverageStatements(hash: Sha256Sum, spatial: Either[GeoFeature, URI])(using Envri): Seq[Statement] =
		spatial match
			case Left(feature) => getGeoFeatureStatements(hash, feature)
			case Right(covUri) =>
				val objectUri = vocab.getStaticObject(hash)
				Seq(makeSt(objectUri, metaVocab.hasSpatialCoverage, covUri.toRdf))


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
