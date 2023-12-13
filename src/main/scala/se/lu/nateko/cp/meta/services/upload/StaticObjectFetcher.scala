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
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import java.time.Instant
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.api.RdfLenses


class StaticObjectReader(
	vocab: CpVocab,
	metaVocab: CpmetaVocab,
	lenses: RdfLenses,
	pidFactory: HandleNetClient.PidFactory,
	citer: CitationMaker
) extends CollectionReader(metaVocab, citer.getItemCitationInfo) with DobjMetaReader(vocab):
	import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*

	def fetchStaticObject(objIri: IRI)(using Envri): TSC2V[StaticObject] = conn ?=>
		if docObjExists(objIri) then
			lenses.documentLens.flatMap: docLens =>
				given TriplestoreConnection = docLens(using conn)
				getExistingDocumentObject(objIri)
		else for
			objFormat <- getObjFormatForDobj(objIri)
			dobjLens <- lenses.dataObjectLens(objFormat.toJava)
			given TriplestoreConnection = dobjLens(using conn)
			dobj <- getExistingDataObject(objIri)
		yield dobj

	def dataObjExists(dobj: IRI): TSC2[Boolean] = resourceHasType(dobj, metaVocab.dataObjectClass)
	def docObjExists(dobj: IRI): TSC2[Boolean] = resourceHasType(dobj, metaVocab.docObjectClass)

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
			parendColls <- getParentCollections(dobj)(using collectionLens)
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
			parendColls <- getParentCollections(doc)(using collectionLens)
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
