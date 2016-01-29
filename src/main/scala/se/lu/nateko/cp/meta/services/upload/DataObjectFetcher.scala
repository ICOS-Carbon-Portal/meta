package se.lu.nateko.cp.meta.services.upload

import java.time.Instant
import org.openrdf.model.{URI, Literal}
import org.openrdf.model.vocabulary.RDF
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.services.CpmetaVocab
import org.openrdf.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.api.EpicPidClient

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
		val fullHash = Sha256Sum.fromHex(getSingleString(dataObjUri, vocab.hasSha256sum).get).get
		val fileName: Option[String] = getSingleString(dataObjUri, vocab.hasName)

		val production: URI = getSingleUri(dataObjUri, vocab.wasProducedBy)
		val producer: URI = getSingleUri(production, vocab.prov.wasAssociatedWith)
		val producerName: Option[String] = getSingleString(producer, vocab.hasName)

		val prodStart = getSingleInstant(production, vocab.prov.startedAtTime).get
		val prodStop = getSingleInstant(production, vocab.prov.endedAtTime).get
		val pos = getPos(production)

		val submission: URI = getSingleUri(dataObjUri, vocab.wasSubmittedBy)
		val submitter: URI = getSingleUri(submission, vocab.prov.wasAssociatedWith)
		val submitterName: Option[String] = getSingleString(submitter, vocab.hasName)

		val submStart = getSingleInstant(submission, vocab.prov.startedAtTime).get
		val submStop = getSingleInstant(submission, vocab.prov.endedAtTime)

		val spec = getSingleUri(dataObjUri, vocab.hasPackageSpec)
		val specFormat = getLabeledResource(spec, vocab.hasFormat)
		val encoding = getLabeledResource(spec, vocab.hasEncoding)
		val dataLevel: Int = getSingleInt(spec, vocab.hasDataLevel)

		DataObject(
			status = getStatus(submStop),
			hash = fullHash,
			accessUrl = vocab.getDataObjectAccessUrl(hash, fileName),
			fileName = fileName,
			pid = submStop.map(_ => pidFactory(hash)),
			production = DataProduction(
				theme = getTheme(producer),
				producer = UriResource(
					uri = producer,
					label = producerName
				),
				start = prodStart,
				stop = prodStop,
				pos = pos,
				coverage = None
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

	private def getStatus(submStop: Option[Instant]): DataObjectStatus =
		if(submStop.isDefined) UploadOk else NotComplete

	private def getTheme(subj: URI): ProducerTheme = {
		val themes = server.getValues(subj, RDF.TYPE).collect{
			case uri: URI if(uri == vocab.atmoStationClass) => ThemeAS
			case uri: URI if(uri == vocab.ecoStationClass) => ThemeES
			case uri: URI if(uri == vocab.oceStationClass) => ThemeOS
		}
		assert(themes.size == 1, s"Expected $subj to be a station of exactly one theme but got ${themes.size}!")
		themes.head
	}

	private def getPos(subj: URI): Option[Map[String, Double]] = {
		val posLat: Option[Double] = getSingleDouble(subj, vocab.hasLatitude)
		val posLon: Option[Double] = getSingleDouble(subj, vocab.hasLongitude)

		(posLat, posLon) match {
			case (Some(posLat), Some(posLon)) => Some(Map("lat" -> posLat, "lon" -> posLon))
			case _ => None
		}
	}

	private def getSingleUri(subj: URI, pred: URI): URI = {
		val vals = server.getValues(subj, pred).collect{
			case uri: URI => uri
		}
		assert(vals.size == 1, "Expected a single value!")
		vals.head
	}

	private def getLabeledResource(subj: URI, pred: URI): UriResource = {
		val vals = server.getValues(subj, pred).collect{
			case uri: URI => uri
		}
		assert(vals.size == 1, "Expected a single value!")
		val label = getSingleString(vals.head, RDFS.LABEL)
		UriResource(vals.head, label)
	}

	private def getSingleString(subj: URI, pred: URI): Option[String] = {
		val vals = server.getValues(subj, pred).collect{
			case lit: Literal => lit.stringValue
		}
		assert(vals.size <= 1, s"Expected at most single value, got ${vals.size}!")
		vals.headOption
	}

	private def getSingleInt(subj: URI, pred: URI): Int = {
		val vals = server.getValues(subj, pred).collect{
			case lit: Literal => lit
		}
		assert(vals.size == 1, "Expected a single value!")
		vals.head.stringValue.toInt
	}

	private def getSingleDouble(subj: URI, pred: URI): Option[Double] = {
		val vals = server.getValues(subj, pred).collect{
			case lit: Literal => lit.stringValue
		}

		assert(vals.size <= 1, s"Expected at most single value, got ${vals.size}!")
		vals.headOption.map(_.toDouble)
	}

	private def getSingleInstant(subj: URI, pred: URI): Option[Instant] = {
		val vals = server.getValues(subj, pred).collect{
			case lit: Literal => lit.stringValue
		}

		assert(vals.size <= 1, s"Expected at most single value, got ${vals.size}!")
		vals.headOption.map(Instant.parse)

	}

}
