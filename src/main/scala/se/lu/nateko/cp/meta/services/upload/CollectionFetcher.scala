package se.lu.nateko.cp.meta.services.upload

import java.net.URI

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.IRI

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*

import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.utils.*
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.services.CpmetaVocab

class CollectionFetcherLite(val server: InstanceServer, vocab: CpVocab) extends CpmetaFetcher {

	val memberProp = metaVocab.dcterms.hasPart

	def getTitle(collUri: IRI): String = getSingleString(collUri, metaVocab.dcterms.title)

	def collectionExists(collUri: IRI): Boolean =
		server.hasStatement(collUri, RDF.TYPE, metaVocab.collectionClass)

	def fetchLite(collUri: IRI): Option[UriResource] = {
		if(collectionExists(collUri)) Some(
			UriResource(collUri.toJava, Some(getTitle(collUri)), Nil)
		) else None
	}

	def getParentCollections(dobj: IRI): Seq[UriResource] = {
		val allIris = server.getStatements(None, Some(memberProp), Some(dobj))
			.map(_.getSubject)
			.collect{case iri: IRI => iri}
			.toIndexedSeq

		val deprecatedColls = allIris.flatMap(getPreviousVersions).toSet

		allIris.flatMap(fetchLite).filterNot(res => deprecatedColls.contains(res.uri))
	}

	def getCreatorIfCollExists(hash: Sha256Sum)(using Envri): Option[IRI] = {
		val collUri = vocab.getCollection(hash)
		server.getUriValues(collUri, metaVocab.dcterms.creator, InstanceServer.AtMostOne).headOption
	}

	def collectionExists(coll: Sha256Sum)(using Envri): Boolean = collectionExists(vocab.getCollection(coll))
}

class CollectionFetcher(
	server: InstanceServer,
	plainFetcher: PlainStaticObjectFetcher,
	citer: CitationMaker
) extends CollectionFetcherLite(server, citer.vocab) {collFetcher =>

	def fetchStatic(hash: Sha256Sum)(using Envri): Option[StaticCollection] = {
		val collUri = citer.vocab.getCollection(hash)
		if(collectionExists(collUri)) Some(getExistingStaticColl(collUri, Some(hash)))
		else None
	}

	private def getExistingStaticColl(coll: IRI, hashOpt: Option[Sha256Sum] = None)(using Envri): StaticCollection = {
		val dct = metaVocab.dcterms

		val members = server.getUriValues(coll, memberProp).map{item =>
			if(collectionExists(item)) getExistingStaticColl(item)
			else plainFetcher.getPlainStaticObject(item)
		}.sortBy(_ match{
			case coll: StaticCollection => coll.title
			case dobj: PlainStaticObject => dobj.name
		})

		val init = StaticCollection(
			res = coll.toJava,
			hash = hashOpt.getOrElse(Sha256Sum.fromBase64Url(coll.getLocalName).get),
			members = members,
			creator = getOrganization(getSingleUri(coll, dct.creator)),
			title = getTitle(coll),
			description = getOptionalString(coll, dct.description),
			nextVersion = getNextVersionAsUri(coll),
			latestVersion = getLatestVersion(coll),
			previousVersion = getPreviousVersion(coll).flattenToSeq.headOption,
			doi = getOptionalString(coll, metaVocab.hasDoi),
			documentation = getOptionalUri(coll, RDFS.SEEALSO).map(plainFetcher.getPlainStaticObject),
			references = References.empty
		)
		val citerRefs = citer.getItemCitationInfo(init)
		//TODO Consider adding collection-specific logic for licence information
		val updatedRefs = citerRefs.copy(title = Some(init.title))
		init.copy(references = updatedRefs)
	}

}

class CollectionReaderLite(vocab: CpVocab, metaVocab: CpmetaVocab) extends CpmetaReader(metaVocab):
	import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*

	val memberProp = metaVocab.dcterms.hasPart

	def getTitle(collUri: IRI): TSC2V[String] = getSingleString(collUri, metaVocab.dcterms.title)

	def collectionExists(collUri: IRI): TSC2[Boolean] =
		hasStatement(collUri, RDF.TYPE, metaVocab.collectionClass)

	def fetchLite(collUri: IRI): TSC2V[UriResource] =
		if collectionExists(collUri) then
			getTitle(collUri).map: title =>
				UriResource(collUri.toJava, Some(title), Nil)
		else Validated.error("collection does not exist")


	def getParentCollections(dobj: IRI): TSC2V[Seq[UriResource]] =
		val allIris = getStatements(None, Some(memberProp), Some(dobj))
			.map(_.getSubject)
			.collect{case iri: IRI => iri}
			.toIndexedSeq

		for
			deprecatedColls <- Validated.sequence(allIris.map(getPreviousVersions))
			allParentCols <- Validated.sequence(allIris.map(fetchLite))
		yield
			val deprecatedSet = deprecatedColls.flatten.toSet
			allParentCols.filterNot(res => deprecatedSet.contains(res.uri))


	def getCreatorIfCollExists(hash: Sha256Sum)(using Envri): TSC2V[Option[IRI]] =
		getOptionalUri(vocab.getCollection(hash), metaVocab.dcterms.creator)


	def collectionExists(coll: Sha256Sum)(using Envri): TSC2[Boolean] =
		collectionExists(vocab.getCollection(coll))

class CollectionReader(vocab: CpVocab, metaVocab: CpmetaVocab, citer: CitationMaker) extends CollectionReaderLite(vocab, metaVocab):
	import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*

	def fetchStatic(hash: Sha256Sum)(using Envri): TSC2V[Option[StaticCollection]] =
		val collUri = citer.vocab.getCollection(hash)
		val test = getPosition((collUri))
		for
			staticColl <- getExistingStaticColl(collUri, Some(hash))
		yield
			if collectionExists(collUri) then Some(staticColl)
			else None

	private def getExistingStaticColl(coll: IRI, hashOpt: Option[Sha256Sum] = None)(using Envri): TSC2V[StaticCollection] =
		val dct = metaVocab.dcterms

		val membersValidSeq = Validated.sequence(
			getUriValues(coll, memberProp).map: item =>
				if collectionExists(item) then getExistingStaticColl(item)
				else getPlainStaticObject(item)
		)
		val membersSeq = for
			memb <- membersValidSeq
		yield
			memb.sortBy(_ match{
				case coll: StaticCollection => coll.title
				case dobj: PlainStaticObject => dobj.name
			})

		for
			creatorUri <- getSingleUri(coll, dct.creator)
			members <- membersSeq
			creator <- getOrganization(creatorUri)
			title <- getTitle(coll)
			description <- getOptionalString(coll, dct.description)
			nextVersion <- getNextVersionAsUri(coll)
			latestVersion <- getLatestVersion(coll)
			previousVersion <- getPreviousVersion(coll)
			doi <- getOptionalString(coll, metaVocab.hasDoi)
			documentationUriOpt <- getOptionalUri(coll, RDFS.SEEALSO)
			documentation <- Validated.sinkOption(documentationUriOpt.map(getPlainStaticObject))
		yield
			val init = StaticCollection(
				res = coll.toJava,
				hash = hashOpt.getOrElse(Sha256Sum.fromBase64Url(coll.getLocalName).get),
				members = members,
				creator = creator,
				title = title,
				description = description,
				nextVersion = nextVersion,
				latestVersion = latestVersion,
				previousVersion = previousVersion.flattenToSeq.headOption,
				doi = doi,
				documentation = documentation,
				references = References.empty
			)
			val citerRefs = citer.getItemCitationInfo(init)
			//TODO Consider adding collection-specific logic for licence information
			val updatedRefs = citerRefs.copy(title = Some(init.title))
			init.copy(references = updatedRefs)
