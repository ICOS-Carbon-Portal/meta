package se.lu.nateko.cp.meta.services

import java.util.Date

import org.openrdf.model.{URI, Literal}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.instanceserver.InstanceServer

import java.net.{URI => JavaURI}

class DataPackageFetcher(server: InstanceServer) {

	private implicit val factory = server.factory
	private val vocab = new CpmetaVocab(factory)

	def getPackage(hash: Sha256Sum): DataPackage = {
		val packUri = vocab.getFile(hash)

		val production: URI = getSingleUri(packUri, vocab.wasProducedBy)
		val producer: URI = getSingleUri(production, vocab.prov.wasAssociatedWith)
		val producerName: String = getSingleString(producer, vocab.hasName)

		DataPackage(
			spec = DataPackageSpec(
				format = new JavaURI("specFormat"),
				encoding = new JavaURI("encoding"),
				dataLevel = 2
			),
			submission = PackageSubmission(
				submittingOrg = UriResource(
					uri = new JavaURI("submittingOrgURI"),
					label = Option("Label")
				),
				start = new Date(),
				stop = Option(new Date())
			),
			production = PackageProduction(
				producer = UriResource(
					uri = new JavaURI(producer.stringValue),
					label = Option(producerName)
				)
			),
			hash = hash
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
		assert(vals.size == 1, "Expected a single value!")
		vals.head.stringValue
	}

}
