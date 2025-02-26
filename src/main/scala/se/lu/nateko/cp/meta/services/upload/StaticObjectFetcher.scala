package se.lu.nateko.cp.meta.services.upload

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.api.{HandleNetClient, RdfLens, RdfLenses}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import java.time.Instant


class StaticObjectReader(
	vocab: CpVocab,
	metaVocab: CpmetaVocab,
	lenses: RdfLenses,
	pidFactory: HandleNetClient.PidFactory,
	citer: CitationMaker
) extends CollectionReader(metaVocab, citer.getItemCitationInfo) with DobjMetaReader(vocab):
	import StatementSource.{
		resourceHasType,
		getSingleUri,
		getOptionalUri,
		getHashsum,
		getSingleString,
		getOptionalLong,
		getOptionalString
	}
	import RdfLens.{DobjConn, DobjLens, DocConn, GlobConn}

	def fetchStaticObject(objIri: IRI)(using Envri, GlobConn): Validated[StaticObject] =
		if docObjExists(objIri) then
			for
				given DocConn <- lenses.documentLens
				docObj <- getExistingDocumentObject(objIri)
			yield docObj
		else for
			given DobjConn <- getLensForDataObj(objIri)
			dobj <- getExistingDataObject(objIri)
		yield dobj

	def getLensForDataObj(dobjIri: IRI)(using Envri, GlobConn): Validated[DobjLens] =
		getObjFormatForDobj(dobjIri).flatMap: objFormat =>
			lenses.dataObjectLens(objFormat.toJava)

	def dataObjExists(dobj: IRI)(using GlobConn): Boolean = resourceHasType(dobj, metaVocab.dataObjectClass)
	def docObjExists(dobj: IRI)(using DocConn): Boolean = resourceHasType(dobj, metaVocab.docObjectClass)

	def getExistingDataObject(dobj: IRI)(using envri: Envri, dobjConn: DobjConn): Validated[DataObject] =
		for
			specIri <- getSingleUri(dobj, metaVocab.hasObjectSpec)
			docLens <- lenses.documentLens
			docConn: DocConn = docLens
			spec <- getSpecification(specIri)(using docConn)
			valTypeLookupUri <- getOptionalUri(specIri, metaVocab.containsDataset)
			valTypeLookup <- valTypeLookupUri.fold(Validated(VarMetaLookup(Nil)))(getValTypeLookup)
			productionUri <- getOptionalUri(dobj, metaVocab.wasProducedBy)
			productionOpt <- productionUri.map(getDataProduction(dobj, _, docConn)).sinkOption
			levelSpecificInfo <- spec.specificDatasetType match
				case DatasetType.SpatioTemporal =>
					getSpatioTempMeta(dobj, valTypeLookup, productionOpt)(using dobjConn, docConn).map(Left.apply)
				case DatasetType.StationTimeSeries =>
					getStationTimeSerMeta(dobj, valTypeLookup, productionOpt, docConn).map(Right.apply)
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
				// next and previous versions can have different format, so may be in an arbitrary RDF graph
				nextVersion = getNextVersionAsUri(dobj)(using RdfLens.global),
				latestVersion = getLatestVersion(dobj)(using RdfLens.global),
				previousVersion = getPreviousVersion(dobj)(using RdfLens.global).mapO3(_.toJava),
				parentCollections = parendColls,
				references = References.empty
			)
			refs <- citer.getCitationInfo(init)
		yield
			init.copy(references = refs)
	end getExistingDataObject

	def getExistingDocumentObject(doc: IRI)(using Envri, DocConn): Validated[DocObject] =
		for
			hash <- getHashsum(doc, metaVocab.hasSha256sum)
			fileName <- getSingleString(doc, metaVocab.hasName)
			sizeOpt <- getOptionalLong(doc, metaVocab.hasSizeInBytes)
			submissionUri <- getSingleUri(doc, metaVocab.wasSubmittedBy)
			submission <- getSubmission(submissionUri)
			doiOpt <- getOptionalString(doc, metaVocab.hasDoi)
			descriptionOpt <- getOptionalString(doc, metaVocab.dcterms.description)
			titleOpt <- getOptionalString(doc, metaVocab.dcterms.title)
			authors <- getContributors(doc, metaVocab.dcterms.creator)
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
					authors = Option(authors.toSeq)
				)
			)
			refs <- citer.getCitationInfo(init)
		yield
			init.copy(references = refs)

	private def getPid(hash: Sha256Sum, format: URI)(using Envri): Option[String] =
		if(metaVocab.wdcggFormat === format) None else Some(pidFactory.getPid(hash))

	private def getAccessUrl(hash: Sha256Sum, spec: DataObjectSpec)(using Envri, DobjConn): Validated[Option[URI]] =
		if metaVocab.wdcggFormat === spec.format.self.uri then
			Validated(Some(new URI("https://gaw.kishou.go.jp/")))
		else
			val dobj = vocab.getStaticObject(hash)
			for uri <- getOptionalUri(dobj, RDFS.SEEALSO)
			yield uri.map(_.toJava).orElse(
				if(spec.dataLevel < 1 && spec.theme.self.uri === vocab.atmoTheme) None
				else Some(vocab.getStaticObjectAccessUrl(hash))
			)
