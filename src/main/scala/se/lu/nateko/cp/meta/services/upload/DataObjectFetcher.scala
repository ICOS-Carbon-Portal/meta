package se.lu.nateko.cp.meta.services.upload

import java.net.URI

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j._
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.core.data.Envri.Envri

class DataObjectFetcher(
	protected val server: InstanceServer,
	protected val vocab: CpVocab,
	pidFactory: Sha256Sum => String
) extends CpmetaFetcher {

	def fetch(hash: Sha256Sum)(implicit envri: Envri): Option[DataObject] = {
		val dataObjUri = vocab.getDataObject(hash)
		if(server.hasStatement(dataObjUri, RDF.TYPE, metaVocab.dataObjectClass))
			Some(getExistingDataObject(hash))
		else None
	}

	private def getExistingDataObject(hash: Sha256Sum)(implicit envri: Envri): DataObject = {
		val dobj = vocab.getDataObject(hash)

		val production: Option[DataProduction] = getOptionalUri(dobj, metaVocab.wasProducedBy)
			.map(getDataProduction)

		val fileName = getSingleString(dobj, metaVocab.hasName)
		val spec = getSpecification(getSingleUri(dobj, metaVocab.hasObjectSpec))
		val submission = getSubmission(getSingleUri(dobj, metaVocab.wasSubmittedBy))

		val levelSpecificInfo = if(spec.dataLevel == 3 || spec.self.uri === vocab.getObjectSpecification("ingosArchive"))
				Left(getL3Meta(dobj, production))
			else
				Right(getL2Meta(dobj, production))

		DataObject(
			hash = getHashsum(dobj, metaVocab.hasSha256sum),
			accessUrl = getAccessUrl(hash, spec),
			fileName = fileName,
			size = getOptionalLong(dobj, metaVocab.hasSizeInBytes),
			pid = submission.stop.flatMap(_ => getPid(hash, spec.format.uri)),
			doi = getOptionalString(dobj, metaVocab.hasDoi),
			submission = submission,
			specification = spec,
			specificInfo = levelSpecificInfo,
			nextVersion = getNextVersion(dobj),
			previousVersion = getPreviousVersion(dobj)
		)
	}

	private def getPid(hash: Sha256Sum, format: URI): Option[String] = {
		if(metaVocab.wdcggFormat === format) None else Some(pidFactory(hash))
	}

	private def getAccessUrl(hash: Sha256Sum, spec: DataObjectSpec)(implicit envri: Envri): Option[URI] = {

		if(metaVocab.wdcggFormat === spec.format.uri)
			Some(new URI("http://ds.data.jma.go.jp/gmd/wdcgg/wdcgg.html"))
		else {
			val dobj = vocab.getDataObject(hash)
			getOptionalUri(dobj, RDFS.SEEALSO).map(_.toJava).orElse(
				if(spec.dataLevel < 1) None
				else Some(vocab.getDataObjectAccessUrl(hash))
			)
		}
	}

	private def getNextVersion(dobj: IRI): Option[URI] = {
		server.getStatements(None, Some(metaVocab.isNextVersionOf), Some(dobj))
			.toSeq.headOption.collect{
				case Rdf4jStatement(next, _, _) => next.toJava
			}
	}

	private def getPreviousVersion(dobj: IRI): Option[URI] =
		getOptionalUri(dobj, metaVocab.isNextVersionOf).map(_.toJava)

}
