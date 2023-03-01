package se.lu.nateko.cp.meta.services.upload

import java.net.URI

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.IRI

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.utils.*
import se.lu.nateko.cp.meta.services.citation.CitationMaker

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

	def getCreatorIfCollExists(hash: Sha256Sum)(implicit envri: Envri): Option[IRI] = {
		val collUri = vocab.getCollection(hash)
		server.getUriValues(collUri, metaVocab.dcterms.creator, InstanceServer.AtMostOne).headOption
	}

	def collectionExists(coll: Sha256Sum)(implicit envri: Envri): Boolean = collectionExists(vocab.getCollection(coll))
}

class CollectionFetcher(
	server: InstanceServer,
	plainFetcher: PlainStaticObjectFetcher,
	citer: CitationMaker
) extends CollectionFetcherLite(server, citer.vocab) {collFetcher =>

	def fetchStatic(hash: Sha256Sum)(implicit envri: Envri): Option[StaticCollection] = {
		val collUri = citer.vocab.getCollection(hash)
		if(collectionExists(collUri)) Some(getExistingStaticColl(collUri))
		else None
	}

	private def getExistingStaticColl(coll: IRI)(using Envri): StaticCollection = {
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
			members = members,
			creator = getOrganization(getSingleUri(coll, dct.creator)),
			title = getTitle(coll),
			description = getOptionalString(coll, dct.description),
			nextVersion = getNextVersion(coll),
			previousVersion = getPreviousVersion(coll).flattenToSeq.headOption,
			doi = getOptionalString(coll, metaVocab.hasDoi),
			references = References.empty
		)
		//TODO Consider adding collection-specific logic for licence information
		init.copy(references = citer.getItemCitationInfo(init))
	}

}
