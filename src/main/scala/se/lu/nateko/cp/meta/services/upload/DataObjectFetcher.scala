package se.lu.nateko.cp.meta.services.upload

import java.time.Instant
import org.openrdf.model.{URI, Literal}
import org.openrdf.model.vocabulary.RDF
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.core.data.DataTheme._
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
		val fileName: Option[String] = getOptionalString(dataObjUri, vocab.hasName)

		val production: URI = getSingleUri(dataObjUri, vocab.wasProducedBy)
		val producer: URI = getSingleUri(production, vocab.prov.wasAssociatedWith)
		val producerName: String = getSingleString(producer, vocab.hasName)

		val prodTimeInterval = for(
			start <- getOptionalInstant(production, vocab.prov.startedAtTime);
			stop <- getOptionalInstant(production, vocab.prov.endedAtTime)
		) yield TimeInterval(start, stop)

		val pos = for(
			posLat <- getOptionalDouble(producer, vocab.hasLatitude);
			posLon <- getOptionalDouble(producer, vocab.hasLongitude)
		) yield Position(posLat, posLon)


		val submission: URI = getSingleUri(dataObjUri, vocab.wasSubmittedBy)
		val submitter: URI = getSingleUri(submission, vocab.prov.wasAssociatedWith)
		val submitterName: Option[String] = getOptionalString(submitter, vocab.hasName)

		val submStart = getSingleInstant(submission, vocab.prov.startedAtTime)
		val submStop = getOptionalInstant(submission, vocab.prov.endedAtTime)

		val spec = getSingleUri(dataObjUri, vocab.hasObjectSpec)
		val specFormat = getLabeledResource(spec, vocab.hasFormat)
		val encoding = getLabeledResource(spec, vocab.hasEncoding)
		val dataLevel: Int = getSingleInt(spec, vocab.hasDataLevel)

		DataObject(
			hash = getHashsum(dataObjUri, vocab.hasSha256sum),
			accessUrl = vocab.getDataObjectAccessUrl(hash, fileName),
			fileName = fileName,
			pid = submStop.flatMap(_ => getPid(hash, specFormat.uri)),
			production = DataProduction(
				producer = DataProducer(
					uri = producer,
					label = producerName,
					theme = getTheme(producer),
					pos = pos,
					//TODO: Add support for geoJson
					coverage = None
				),
				timeInterval = prodTimeInterval
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
				dataLevel = dataLevel,
				datasetSpec = None
			)
		)
	}

	private def getPid(hash: Sha256Sum, format: URI): Option[String] = {
		if(format == vocab.wdcggFormat) None else Some(pidFactory(hash))
	}

	private def getTheme(subj: URI): DataTheme = {
		import vocab.{atmoStationClass, ecoStationClass, oceStationClass, tcClass, cfClass, atc, etc, otc, cp, cal}

		val themes = server.getValues(subj, RDF.TYPE).collect{
			case `atmoStationClass` => Atmosphere
			case `ecoStationClass` => Ecosystem
			case `oceStationClass` => Ocean
			case `tcClass` => subj match{
				case `atc` => Atmosphere
				case `etc` => Ecosystem
				case `otc` => Ocean
			}
			case `cfClass` => subj match{
				case `cp` => CP
				case `cal` => CAL
			}
		}
		assert(themes.size == 1, s"Expected $subj to be a station of exactly one theme but got ${themes.size} themes!")
		themes.head
	}

	private def getSingleUri(subj: URI, pred: URI): URI =
		server.getUriValues(subj, pred, InstanceServer.ExactlyOne).head

	private def getLabeledResource(subj: URI, pred: URI): UriResource = {
		val uri = getSingleUri(subj, pred)
		UriResource(uri, label = getOptionalString(uri, RDFS.LABEL))
	}

	private def getOptionalString(subj: URI, pred: URI): Option[String] =
		server.getStringValues(subj, pred, InstanceServer.AtMostOne).headOption

	private def getSingleString(subj: URI, pred: URI): String =
		server.getStringValues(subj, pred, InstanceServer.ExactlyOne).head

	private def getSingleInt(subj: URI, pred: URI): Int =
		server.getIntValues(subj, pred, InstanceServer.ExactlyOne).head

	private def getOptionalDouble(subj: URI, pred: URI): Option[Double] =
		server.getDoubleValues(subj, pred, InstanceServer.AtMostOne).headOption

	private def getOptionalInstant(subj: URI, pred: URI): Option[Instant] =
		server.getLiteralValues(subj, pred, XMLSchema.DATETIME, InstanceServer.AtMostOne).headOption.map(Instant.parse)

	private def getSingleInstant(subj: URI, pred: URI): Instant =
		server.getLiteralValues(subj, pred, XMLSchema.DATETIME, InstanceServer.ExactlyOne).map(Instant.parse).head

	private def getHashsum(dataObjUri: URI, pred: URI): Sha256Sum = {
		val hex: String = server.getLiteralValues(dataObjUri, pred, XMLSchema.HEXBINARY, InstanceServer.ExactlyOne).head
		Sha256Sum.fromHex(hex).get
	}

}
