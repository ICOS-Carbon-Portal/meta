package se.lu.nateko.cp.meta.services.upload

import java.net.URI
import java.time.Instant

import scala.concurrent.Future

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XMLSchema

import akka.actor.ActorSystem
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.DataProductionDto
import se.lu.nateko.cp.meta.DataSubmitterConfig
import se.lu.nateko.cp.meta.ElaboratedProductMetadata
import se.lu.nateko.cp.meta.StationDataMetadata
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.SpatialCoverage
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.services.upload.completion.UploadCompleter
import se.lu.nateko.cp.meta.api.EpicPidClient

class UploadService(servers: DataObjectInstanceServers, sparql: SparqlRunner, conf: UploadServiceConfig)(implicit system: ActorSystem) {
	private implicit val factory = servers.icosMeta.factory
	import servers.{ metaVocab, vocab }
	import system.dispatcher
	private val validator = new UploadValidator(servers, conf)
	private val epic = new EpicPidClient(conf.epicPid)
	private val completer = new UploadCompleter(servers, epic)
	private val metaUpdater = new MetadataUpdater(vocab, metaVocab, sparql)

	def fetchDataObj(hash: Sha256Sum): Option[DataObject] = {
		val server = servers.getInstServerForDataObj(hash).get
		val objectFetcher = new DataObjectFetcher(server, vocab, metaVocab, epic.getPid)
		objectFetcher.fetch(hash)
	}

	def registerUpload(meta: UploadMetadataDto, uploader: UserId): Future[String] = {
		val serverTry = for(
			_ <- validator.validateUpload(meta, uploader);
			submitterConf <- validator.getSubmitterConfig(meta);
			format <- servers.getObjSpecificationFormat(meta.objectSpecification.toRdf);
			server <- servers.getInstServerForFormat(format)
		) yield (server, submitterConf)

		for(
			(server, submitterConf) <- Future.fromTry(serverTry);
			newStatements = getStatements(meta, submitterConf);
			currentStatements <- metaUpdater.getCurrentStatements(meta.hashSum, server);
			updates = metaUpdater.calculateUpdates(meta.hashSum, currentStatements, newStatements);
			_ <- Future.fromTry(server.applyAll(updates))
		) yield{
			vocab.getDataObjectAccessUrl(meta.hashSum, meta.fileName).toString
		}
	}

	def checkPermissions(submitter: URI, userId: String): Boolean =
		conf.submitters.values
			.filter(_.submittingOrganization === submitter)
			.exists(_.authorizedUserIds.contains(userId))

	def completeUpload(hash: Sha256Sum, info: UploadCompletionInfo): Future[String] =
		completer.completeUpload(hash, info).map(_.message)


	private def getStatements(meta: UploadMetadataDto, submConf: DataSubmitterConfig): Seq[Statement] = {
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
			makeSt(submissionUri, metaVocab.prov.wasAssociatedWith, submConf.submittingOrganization.toRdf)
		)
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

	private def getSpatialCoverageStatements(hash: Sha256Sum, spatial: Either[SpatialCoverage, URI]): Seq[Statement] = {
		val objectUri = vocab.getDataObject(hash)
		spatial match{
			case Left(coverage) =>
				val covUri = vocab.getSpatialCoverate(hash)
				Seq(
					makeSt(objectUri, metaVocab.hasSpatialCoverage, covUri),
					makeSt(covUri, RDF.TYPE, metaVocab.latLonBoxClass),
					makeSt(covUri, metaVocab.hasNothernBound, vocab.lit(coverage.max.lat)),
					makeSt(covUri, metaVocab.hasSouthernBound, vocab.lit(coverage.min.lat)),
					makeSt(covUri, metaVocab.hasWesternBound, vocab.lit(coverage.min.lon)),
					makeSt(covUri, metaVocab.hasEasternBound, vocab.lit(coverage.max.lon))
				) ++
				makeSt(covUri, RDFS.LABEL, coverage.label.map(vocab.lit))
			case Right(existing) =>
				// TODO Add a validation that 'existing' actually exists
				/* TODO Protect 'existing' coverage object in the metadata update scenario
				 *  (otherwise, 'existing' may be removed, if it is in the same RDF graph and not used
				 *  by this object any more; it may be needed by others)
				 */
				Seq(makeSt(objectUri, metaVocab.hasSpatialCoverage, existing.toRdf))
		}
	}

	private def makeSt(subj: IRI, pred: IRI, obj: Option[Value]): Iterable[Statement] =
		obj.map(factory.createStatement(subj, pred, _))

	private def makeSt(subj: IRI, pred: IRI, obj: Value): Statement = factory.createStatement(subj, pred, obj)

}
