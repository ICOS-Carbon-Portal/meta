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
import java.net.{URI => JavaUri}
import se.lu.nateko.cp.meta.services.CpVocab

class DataObjectFetcher(server: InstanceServer, vocab: CpVocab, metaVocab: CpmetaVocab, pidFactory: Sha256Sum => String) {

	private implicit val factory = metaVocab.factory

	def fetch(hash: Sha256Sum): Option[DataObject] = {
		val dataObjUri = vocab.getDataObject(hash)
		if(server.hasStatement(dataObjUri, RDF.TYPE, metaVocab.dataObjectClass))
			Some(getExistingDataObject(hash))
		else None
	}

	private def getExistingDataObject(hash: Sha256Sum): DataObject = {
		val dataObjUri = vocab.getDataObject(hash)
		val fileName: Option[String] = getOptionalString(dataObjUri, metaVocab.hasName)

		val production: URI = getSingleUri(dataObjUri, metaVocab.wasProducedBy)
		val producer: URI = getSingleUri(production, metaVocab.prov.wasAssociatedWith)
		val producerName: String = getSingleString(producer, metaVocab.hasName)

		val prodTimeInterval = for(
			start <- getOptionalInstant(production, metaVocab.prov.startedAtTime);
			stop <- getOptionalInstant(production, metaVocab.prov.endedAtTime)
		) yield TimeInterval(start, stop)

		val pos = for(
			posLat <- getOptionalDouble(producer, metaVocab.hasLatitude);
			posLon <- getOptionalDouble(producer, metaVocab.hasLongitude)
		) yield Position(posLat, posLon)


		val submission: URI = getSingleUri(dataObjUri, metaVocab.wasSubmittedBy)
		val submitter: URI = getSingleUri(submission, metaVocab.prov.wasAssociatedWith)
		val submitterName: Option[String] = getOptionalString(submitter, metaVocab.hasName)

		val submStart = getSingleInstant(submission, metaVocab.prov.startedAtTime)
		val submStop = getOptionalInstant(submission, metaVocab.prov.endedAtTime)

		val spec = getSingleUri(dataObjUri, metaVocab.hasObjectSpec)
		val specFormat = getLabeledResource(spec, metaVocab.hasFormat)
		val encoding = getLabeledResource(spec, metaVocab.hasEncoding)
		val dataLevel: Int = getSingleInt(spec, metaVocab.hasDataLevel)

		DataObject(
			hash = getHashsum(dataObjUri, metaVocab.hasSha256sum),
			accessUrl = getAccessUrl(hash, fileName, specFormat.uri),
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
		if(format == metaVocab.wdcggFormat) None else Some(pidFactory(hash))
	}

	private def getAccessUrl(hash: Sha256Sum, fileName: Option[String], specFormat: URI): Option[JavaUri] = {
		if(specFormat == metaVocab.wdcggFormat) None
		else Some(vocab.getDataObjectAccessUrl(hash, fileName))
	}

	private def getTheme(subj: URI): DataTheme = {
		import metaVocab.{atmoStationClass, ecoStationClass, oceStationClass, tcClass, cfClass, orgClass, atc, etc, otc, cp, cal}

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
			case `orgClass` => NonICOS
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
