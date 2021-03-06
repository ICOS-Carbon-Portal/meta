package se.lu.nateko.cp.meta.services.upload

import java.net.URI

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.services.citation.CitationMaker

class StaticObjectFetcher(
	val server: InstanceServer,
	collFetcher: CollectionFetcherLite,
	val plainObjFetcher: PlainStaticObjectFetcher,
	pidFactory: HandleNetClient.PidFactory,
	citer: CitationMaker
) extends DobjMetaFetcher {

	override protected val vocab = citer.vocab

	def fetch(hash: Sha256Sum)(implicit envri: Envri): Option[StaticObject] = {
		val dataObjUri = vocab.getStaticObject(hash)
		if(server.hasStatement(dataObjUri, RDF.TYPE, metaVocab.dataObjectClass))
			Some(getExistingDataObject(hash))
		else if(server.hasStatement(dataObjUri, RDF.TYPE, metaVocab.docObjectClass))
			Some(getExistingDocumentObject(hash))
		else None
	}

	private def getExistingDataObject(hash: Sha256Sum)(implicit envri: Envri): DataObject = {
		val dobj = vocab.getStaticObject(hash)

		val production: Option[DataProduction] = getOptionalUri(dobj, metaVocab.wasProducedBy)
			.map(getDataProduction(dobj, _))

		val specIri = getSingleUri(dobj, metaVocab.hasObjectSpec)
		val spec = getSpecification(specIri)
		val submission = getSubmission(getSingleUri(dobj, metaVocab.wasSubmittedBy))
		val valTypeLookup = getOptionalUri(specIri, metaVocab.containsDataset)
			.fold(new ValueTypeLookup[IRI](Nil))(getValTypeLookup)

		val levelSpecificInfo = if(spec.dataLevel == 3 || CpVocab.isIngosArchive(specIri))
				Left(getL3Meta(dobj, valTypeLookup, production))
			else
				Right(getL2Meta(dobj, valTypeLookup, production))

		val init = DataObject(
			hash = getHashsum(dobj, metaVocab.hasSha256sum),
			accessUrl = getAccessUrl(hash, spec),
			fileName = getSingleString(dobj, metaVocab.hasName),
			size = getOptionalLong(dobj, metaVocab.hasSizeInBytes),
			pid = submission.stop.flatMap(_ => getPid(hash, spec.format.uri)),
			doi = getOptionalString(dobj, metaVocab.hasDoi),
			submission = submission,
			specification = spec,
			specificInfo = levelSpecificInfo,
			nextVersion = getNextVersion(dobj),
			previousVersion = getPreviousVersion(dobj),
			parentCollections = collFetcher.getParentCollections(dobj),
			references = References.empty
		)
		init.copy(references = citer.getCitationInfo(init))
	}

	private def getExistingDocumentObject(hash: Sha256Sum)(implicit envri: Envri): DocObject = {
		val doc = vocab.getStaticObject(hash)
		val submission = getSubmission(getSingleUri(doc, metaVocab.wasSubmittedBy))
		val init = DocObject(
			hash = getHashsum(doc, metaVocab.hasSha256sum),
			accessUrl = Some(vocab.getStaticObjectAccessUrl(hash)),
			fileName = getSingleString(doc, metaVocab.hasName),
			size = getOptionalLong(doc, metaVocab.hasSizeInBytes),
			pid = submission.stop.map(_ => pidFactory.getPid(hash)),
			doi = getOptionalString(doc, metaVocab.hasDoi),
			submission = submission,
			nextVersion = getNextVersion(doc),
			previousVersion = getPreviousVersion(doc),
			parentCollections = collFetcher.getParentCollections(doc),
			references = References.empty
		)
		init.copy(references = citer.getCitationInfo(init))
	}

	private def getPid(hash: Sha256Sum, format: URI)(implicit envri: Envri): Option[String] = {
		if(metaVocab.wdcggFormat === format) None else Some(pidFactory.getPid(hash))
	}

	private def getAccessUrl(hash: Sha256Sum, spec: DataObjectSpec)(implicit envri: Envri): Option[URI] = {

		if(metaVocab.wdcggFormat === spec.format.uri)
			Some(new URI("http://ds.data.jma.go.jp/gmd/wdcgg/wdcgg.html"))
		else {
			val dobj = vocab.getStaticObject(hash)
			getOptionalUri(dobj, RDFS.SEEALSO).map(_.toJava).orElse(
				if(spec.dataLevel < 1) None
				else Some(vocab.getStaticObjectAccessUrl(hash))
			)
		}
	}
}

class PlainStaticObjectFetcher(allDataObjServer: InstanceServer) extends FetchingHelper{
	private val metaVocab = new CpmetaVocab(allDataObjServer.factory)
	override def server = allDataObjServer

	def getPlainStaticObject(dobj: IRI) = PlainStaticObject(
		dobj.toJava,
		getHashsum(dobj, metaVocab.hasSha256sum),
		getSingleString(dobj, metaVocab.hasName)
	)
}
