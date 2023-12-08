package se.lu.nateko.cp.meta.services.upload

import java.net.URI

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import java.time.Instant
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.api.RdfLenses

class StaticObjectFetcher(
	val server: InstanceServer,
	collFetcher: CollectionFetcherLite,
	val plainObjFetcher: PlainStaticObjectFetcher,
	pidFactory: HandleNetClient.PidFactory,
	citer: CitationMaker
) extends DobjMetaFetcher {

	override protected val vocab = null//citer.vocab

	def fetch(hash: Sha256Sum)(using Envri): Option[StaticObject] = {
		val dataObjUri = vocab.getStaticObject(hash)
		if(server.hasStatement(dataObjUri, RDF.TYPE, metaVocab.dataObjectClass))
			Some(getExistingDataObject(hash))
		else if(server.hasStatement(dataObjUri, RDF.TYPE, metaVocab.docObjectClass))
			Some(getExistingDocumentObject(hash))
		else None
	}

	private def getExistingDataObject(hash: Sha256Sum)(using Envri): DataObject = {
		val dobj = vocab.getStaticObject(hash)

		val production: Option[DataProduction] = getOptionalUri(dobj, metaVocab.wasProducedBy)
			.map(getDataProduction(dobj, _))

		val specIri = getSingleUri(dobj, metaVocab.hasObjectSpec)
		val spec = getSpecification(specIri)
		val submission = getSubmission(getSingleUri(dobj, metaVocab.wasSubmittedBy))
		val valTypeLookup = getOptionalUri(specIri, metaVocab.containsDataset)
			.fold(VarMetaLookup(Nil))(getValTypeLookup)

		val levelSpecificInfo = spec.specificDatasetType match
			case DatasetType.SpatioTemporal =>
				Left(getSpatioTempMeta(dobj, valTypeLookup, production))
			case DatasetType.StationTimeSeries =>
				Right(getStationTimeSerMeta(dobj, valTypeLookup, production))

		val hasBeenPublished = submission.stop.fold(false)(_.compareTo(Instant.now()) < 0)
		val size = getOptionalLong(dobj, metaVocab.hasSizeInBytes)

		val init = DataObject(
			hash = getHashsum(dobj, metaVocab.hasSha256sum),
			accessUrl = if(hasBeenPublished) getAccessUrl(hash, spec) else None,
			fileName = getSingleString(dobj, metaVocab.hasName),
			size = size,
			pid = if(size.isDefined) getPid(hash, spec.format.self.uri) else None,
			doi = getOptionalString(dobj, metaVocab.hasDoi),
			submission = submission,
			specification = spec,
			specificInfo = levelSpecificInfo,
			nextVersion = getNextVersionAsUri(dobj),
			latestVersion = getLatestVersion(dobj),
			previousVersion = getPreviousVersion(dobj),
			parentCollections = collFetcher.getParentCollections(dobj),
			references = References.empty
		)
		init//.copy(references = citer.getCitationInfo(init))
	}

	private def getExistingDocumentObject(hash: Sha256Sum)(using Envri): DocObject = {
		val doc = vocab.getStaticObject(hash)
		val submission = getSubmission(getSingleUri(doc, metaVocab.wasSubmittedBy))
		val init = DocObject(
			hash = getHashsum(doc, metaVocab.hasSha256sum),
			accessUrl = Some(vocab.getStaticObjectAccessUrl(hash)),
			fileName = getSingleString(doc, metaVocab.hasName),
			size = getOptionalLong(doc, metaVocab.hasSizeInBytes),
			pid = submission.stop.map(_ => pidFactory.getPid(hash)),
			doi = getOptionalString(doc, metaVocab.hasDoi),
			description = getOptionalString(doc, metaVocab.dcterms.description),
			submission = submission,
			nextVersion = getNextVersionAsUri(doc),
			latestVersion = getLatestVersion(doc),
			previousVersion = getPreviousVersion(doc),
			parentCollections = collFetcher.getParentCollections(doc),
			references = References.empty.copy(
				title = getOptionalString(doc, metaVocab.dcterms.title),
				authors = Option(server.getUriValues(doc, metaVocab.dcterms.creator).map(getAgent))
			)
		)
		init//.copy(references = citer.getCitationInfo(init))
	}

	private def getPid(hash: Sha256Sum, format: URI)(using Envri): Option[String] = {
		if(metaVocab.wdcggFormat === format) None else Some(pidFactory.getPid(hash))
	}

	private def getAccessUrl(hash: Sha256Sum, spec: DataObjectSpec)(using Envri): Option[URI] = {

		if(metaVocab.wdcggFormat === spec.format.self.uri)
			Some(new URI("https://gaw.kishou.go.jp/"))
		else {
			val dobj = vocab.getStaticObject(hash)
			getOptionalUri(dobj, RDFS.SEEALSO).map(_.toJava).orElse(
				if(spec.dataLevel < 1 && spec.theme.self.uri === vocab.atmoTheme) None
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
		getOptionalString(dobj, metaVocab.dcterms.title).getOrElse(getSingleString(dobj, metaVocab.hasName))
	)
}



class StaticObjectReader(
	vocab: CpVocab,
	metaVocab: CpmetaVocab,
	collReader: CollectionReader,
	lenses: RdfLenses,
	pidFactory: HandleNetClient.PidFactory,
	citer: CitationMaker
) extends DobjMetaReader(vocab, metaVocab):
	import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*

	// def fetch(hash: Sha256Sum)(using Envri): TSC2V[StaticObject] =
	// 	val dobjUri = vocab.getStaticObject(hash)
	// 	if hasStatement(dobjUri, RDF.TYPE, metaVocab.dataObjectClass) then
	// 		getExistingDataObject(dobjUri)
	// 	else if(hasStatement(dobjUri, RDF.TYPE, metaVocab.docObjectClass))
	// 		getExistingDocumentObject(dobjUri)
	// 	else Validated.error(s"$dobjUri is neither known data- nor a document object")

	def getExistingDataObject(dobj: IRI)(using Envri): TSC2V[DataObject] =
		for
			specIri <- getSingleUri(dobj, metaVocab.hasObjectSpec)
			spec <- getSpecification(specIri)
			valTypeLookupUri <- getOptionalUri(specIri, metaVocab.containsDataset)
			valTypeLookup <- valTypeLookupUri.fold(Validated(VarMetaLookup(Nil)))(getValTypeLookup)
			productionUri <- getOptionalUri(dobj, metaVocab.wasProducedBy)
			productionOpt <- productionUri.map(getDataProduction(dobj, _)).sinkOption
			levelSpecificInfo <- spec.specificDatasetType match
				case DatasetType.SpatioTemporal =>
					getSpatioTempMeta(dobj, valTypeLookup, productionOpt).map(Left.apply)
				case DatasetType.StationTimeSeries =>
					getStationTimeSerMeta(dobj, valTypeLookup, productionOpt).map(Right.apply)
			hash <- getHashsum(dobj, metaVocab.hasSha256sum)
			accessUrl <- getAccessUrl(hash, spec)
			fileName <- getSingleString(dobj, metaVocab.hasName)
			sizeOpt <- getOptionalLong(dobj, metaVocab.hasSizeInBytes)
			doiOpt <- getOptionalString(dobj, metaVocab.hasDoi)
			submissionUri <- getSingleUri(dobj, metaVocab.wasSubmittedBy)
			submission <- getSubmission(submissionUri)
			collectionLens <- lenses.collectionLens
			parendColls <- collReader.getParentCollections(dobj)(using collectionLens)
			hasBeenPublished = submission.stop.fold(false)(_.compareTo(Instant.now()) < 0)
			init = DataObject(
				hash = hash,
				accessUrl = if hasBeenPublished then accessUrl else None,
				fileName = fileName,
				size = sizeOpt,
				pid = if(sizeOpt.isDefined) getPid(hash, spec.format.self.uri) else None,
				doi = doiOpt,
				submission = submission,
				specification = spec,
				specificInfo = levelSpecificInfo,
				// next version can have different format, so may be in an arbitrary RDF graph
				nextVersion = getNextVersionAsUri(dobj)(using globalLens),
				latestVersion = getLatestVersion(dobj),
				previousVersion = getPreviousVersion(dobj).mapO3(_.toJava),
				parentCollections = parendColls,
				references = References.empty
			)
			refs <- citer.getCitationInfo(init)
		yield
			init.copy(references = refs)
	end getExistingDataObject

	def getExistingDocumentObject(doc: IRI)(using Envri): TSC2V[DocObject] =
		for
			hash <- getHashsum(doc, metaVocab.hasSha256sum)
			fileName <- getSingleString(doc, metaVocab.hasName)
			sizeOpt <- getOptionalLong(doc, metaVocab.hasSizeInBytes)
			submissionUri <- getSingleUri(doc, metaVocab.wasSubmittedBy)
			submission <- getSubmission(submissionUri)
			doiOpt <- getOptionalString(doc, metaVocab.hasDoi)
			descriptionOpt <- getOptionalString(doc, metaVocab.dcterms.description)
			titleOpt <- getOptionalString(doc, metaVocab.dcterms.title)
			authors <- Validated.sequence(getUriValues(doc, metaVocab.dcterms.creator).map(getAgent))
			collectionLens <- lenses.collectionLens
			parendColls <- collReader.getParentCollections(doc)(using collectionLens)
			init = DocObject(
				hash = hash,
				accessUrl = Some(vocab.getStaticObjectAccessUrl(hash)),
				fileName = fileName,
				size = sizeOpt,
				pid = submission.stop.map(_ => pidFactory.getPid(hash)),
				doi = doiOpt,
				description = descriptionOpt,
				submission = submission,
				nextVersion = getNextVersionAsUri(doc),
				latestVersion = getLatestVersion(doc),
				previousVersion = getPreviousVersion(doc).mapO3(_.toJava),
				parentCollections = parendColls,
				references = References.empty.copy(
					title = titleOpt,
					authors = Option(authors)
				)
			)
			refs <- citer.getCitationInfo(init)
		yield
			init.copy(references = refs)

	private def getPid(hash: Sha256Sum, format: URI)(using Envri): TSC2[Option[String]] =
		if(metaVocab.wdcggFormat === format) None else Some(pidFactory.getPid(hash))

	private def getAccessUrl(hash: Sha256Sum, spec: DataObjectSpec)(using Envri): TSC2V[Option[URI]] =
		if metaVocab.wdcggFormat === spec.format.self.uri then
			Validated(Some(new URI("https://gaw.kishou.go.jp/")))
		else
			val dobj = vocab.getStaticObject(hash)
			for uri <- getOptionalUri(dobj, RDFS.SEEALSO)
			yield uri.map(_.toJava).orElse(
				if(spec.dataLevel < 1 && spec.theme.self.uri === vocab.atmoTheme) None
				else Some(vocab.getStaticObjectAccessUrl(hash))
			)
