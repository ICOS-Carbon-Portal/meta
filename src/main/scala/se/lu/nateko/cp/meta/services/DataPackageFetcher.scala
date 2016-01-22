package se.lu.nateko.cp.meta.services

import java.time.Instant

import org.openrdf.model.{URI, Literal}
import org.openrdf.model.vocabulary.RDF

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.utils.sesame._

class DataPackageFetcher(server: InstanceServer) {

	private implicit val factory = server.factory
	private val vocab = new CpmetaVocab(factory)

	def fetch(hash: Sha256Sum): Option[DataPackage] = {
		val dataObjUri = vocab.getFile(hash)
		if(server.hasStatement(dataObjUri, RDF.TYPE, vocab.dataPackageClass))
			Some(getExistingDataObject(hash))
		else None
	}

	private def getExistingDataObject(hash: Sha256Sum): DataPackage = {
		val dataObjUri = vocab.getFile(hash)

		val production: URI = getSingleUri(dataObjUri, vocab.wasProducedBy)
		val producer: URI = getSingleUri(production, vocab.prov.wasAssociatedWith)
		val submission: URI = getSingleUri(dataObjUri, vocab.wasSubmittedBy)
		val submittingOrg: URI = getSingleUri(submission, vocab.prov.wasAssociatedWith)
		val submitterName: String = getSingleString(submittingOrg, vocab.hasName)
		val spec = getSingleUri(dataObjUri, vocab.hasPackageSpec)

		val producerName: String = getSingleString(producer, vocab.hasName)
		val start = getSingleInstant(submission, vocab.prov.startedAtTime).get
		val stop = getSingleInstant(submission, vocab.prov.endedAtTime)
		val specFormat = getSingleUri(spec, vocab.hasFormat)
		val encoding = getSingleUri(spec, vocab.hasEncoding)
		val dataLevel: Int = getSingleInt(spec, vocab.hasDataLevel)

		DataPackage(
			hash = hash,
			production = PackageProduction(
				producer = UriResource(
					uri = producer,
					label = Some(producerName)
				)
			),
			submission = PackageSubmission(
				submittingOrg = UriResource(
					uri = submittingOrg,
					label = Some(submitterName)
				),
				start = start,
				stop = stop
			),
			spec = DataPackageSpec(
				format = specFormat,
				encoding = encoding,
				dataLevel = dataLevel
			)
		)
	}

	private def getSingleUri(subj: URI, pred: URI): URI = {
		val vals = server.getValues(subj, pred).collect{
			case uri: URI => uri
		}
		assert(vals.size == 1, "Expected a single value!")
		vals.head
	}

	private def getSingleString(subj: URI, pred: URI): String = {
		val vals = server.getValues(subj, pred).collect{
			case lit: Literal => lit
		}
		assert(vals.size == 1, s"Expected a single value, got ${vals.size}!")
		vals.head.stringValue
	}

	private def getSingleInt(subj: URI, pred: URI): Int = {
		val vals = server.getValues(subj, pred).collect{
			case lit: Literal => lit
		}
		assert(vals.size == 1, "Expected a single value!")
		vals.head.stringValue.toInt
	}

	private def getSingleInstant(subj: URI, pred: URI): Option[Instant] = {
		val vals = server.getValues(subj, pred).collect{
			case lit: Literal => lit.stringValue
		}

		assert(vals.size <= 1, "Expected no more than one value!")
		vals.headOption.map(Instant.parse)

	}

}
