package se.lu.nateko.cp.meta.services.upload

import java.time.Instant
import org.openrdf.model.{URI, Literal}
import org.openrdf.model.vocabulary.RDF
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.core.data.DataTheme._
import se.lu.nateko.cp.meta.core.data.DataObjectStatus._
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.services.CpmetaVocab
import org.openrdf.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.api.EpicPidClient
import org.openrdf.model.vocabulary.XMLSchema

class DataObjectFetcher(server: InstanceServer, pidFactory: Sha256Sum => String) {

	private implicit val factory = server.factory
	private val vocab = new CpmetaVocab(factory)

	def fetch(hash: Sha256Sum): Option[DataObject] = {
		val dataObjUri = vocab.getDataObject(hash)
		if(server.hasStatement(dataObjUri, RDF.TYPE, vocab.dataObjectClass))
			Some(getExistingDataObject(hash))
		else None
	}

	private def getExistingDataObject(hash: Sha256Sum): DataObject = {
		val dataObjUri = vocab.getDataObject(hash)
		val fileName: Option[String] = getSingleString(dataObjUri, vocab.hasName)

		val production: URI = getSingleUri(dataObjUri, vocab.wasProducedBy)
		val producer: URI = getSingleUri(production, vocab.prov.wasAssociatedWith)
		val producerName: String = getSingleString(producer, vocab.hasName).get

		val prodStart = getSingleInstant(production, vocab.prov.startedAtTime).get
		val prodStop = getSingleInstant(production, vocab.prov.endedAtTime).get
		val pos = getPos(producer)

		val submission: URI = getSingleUri(dataObjUri, vocab.wasSubmittedBy)
		val submitter: URI = getSingleUri(submission, vocab.prov.wasAssociatedWith)
		val submitterName: Option[String] = getSingleString(submitter, vocab.hasName)

		val submStart = getSingleInstant(submission, vocab.prov.startedAtTime).get
		val submStop = getSingleInstant(submission, vocab.prov.endedAtTime)

		val spec = getSingleUri(dataObjUri, vocab.hasPackageSpec)
		val specFormat = getLabeledResource(spec, vocab.hasFormat)
		val encoding = getLabeledResource(spec, vocab.hasEncoding)
		val dataLevel: Int = getSingleInt(spec, vocab.hasDataLevel).get

		DataObject(
			status = if(submStop.isDefined) UploadOk else NotComplete,
			hash = getHashsum(dataObjUri),
			accessUrl = vocab.getDataObjectAccessUrl(hash, fileName),
			fileName = fileName,
			pid = submStop.map(_ => pidFactory(hash)),
			production = DataProduction(
				producer = DataProducer(
					uri = producer,
					label = producerName,
					theme = getTheme(producer),
					pos = pos,
					//TODO: Add support for geoJson
					coverage = None
				),
				start = prodStart,
				stop = prodStop
			),
			submission = DataSubmission(
				submitter = UriResource(
					uri = submitter,
					label = submitterName
				),
				start = submStart,
				stop = submStop
			),
			specification = DataObjectSpec(
				format = specFormat,
				encoding = encoding,
				dataLevel = dataLevel
			)
		)
	}

	private def getHashsum(dataObjUri: URI): Sha256Sum = {
		val hex: String = getOptionalSingleLiteral(dataObjUri, vocab.hasSha256sum, XMLSchema.HEXBINARY)
			.getOrElse(throw new Error("Data object metadata lacks SHA-256 hash sum info!"))
		Sha256Sum.fromHex(hex).get
	}

	private def getTheme(subj: URI): DataTheme = {
		import vocab.{atmoStationClass, ecoStationClass, oceStationClass}

		val themes = server.getValues(subj, RDF.TYPE).collect{
			case `atmoStationClass` => Atmosphere
			case `ecoStationClass` => Ecosystem
			case `oceStationClass` => Ocean
		}
		assert(themes.size == 1, s"Expected $subj to be a station of exactly one theme but got ${themes.size} themes!")
		themes.head
	}

	private def getPos(subj: URI): Option[Position] =
		for(
			posLat <- getSingleDouble(subj, vocab.hasLatitude);
			posLon <- getSingleDouble(subj, vocab.hasLongitude)
		) yield Position(posLat, posLon)

	private def getSingleUri(subj: URI, pred: URI): URI = {
		val vals = server.getValues(subj, pred).collect{
			case uri: URI => uri
		}
		assert(vals.size == 1, "Expected a single value!")
		vals.head
	}

	private def getLabeledResource(subj: URI, pred: URI): UriResource = {
		val uri = getSingleUri(subj, pred)
		UriResource(uri, label = getSingleString(uri, RDFS.LABEL))
	}

	private def getSingleString(subj: URI, pred: URI): Option[String] =
		getOptionalSingleLiteral(subj, pred, XMLSchema.STRING)

	private def getSingleInt(subj: URI, pred: URI): Option[Int] =
		getOptionalSingleLiteral(subj, pred, XMLSchema.INTEGER).map(_.toInt)

	private def getSingleDouble(subj: URI, pred: URI): Option[Double] =
		getOptionalSingleLiteral(subj, pred, XMLSchema.DOUBLE).map(_.toDouble)

	private def getSingleInstant(subj: URI, pred: URI): Option[Instant] =
		getOptionalSingleLiteral(subj, pred, XMLSchema.DATETIME).map(Instant.parse)

	private def getOptionalSingleLiteral(subj: URI, pred: URI, dType: URI): Option[String] = {
		val vals = server.getValues(subj, pred).collect{
			case lit: Literal if(lit.getDatatype == dType) => lit.stringValue
		}

		assert(vals.size <= 1, s"Expected at most single value, got ${vals.size}!")
		vals.headOption
	}

}
