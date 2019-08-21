package se.lu.nateko.cp.meta.services.upload

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.IRI

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.utils._

class CollectionFetcherLite(protected val server: InstanceServer, protected val vocab: CpVocab)(implicit envri: Envri) extends CpmetaFetcher {

	val memberProp = metaVocab.dcterms.hasPart

	def getTitle(collUri: IRI): String = getSingleString(collUri, metaVocab.dcterms.title)

	def collectionExists(collUri: IRI): Boolean =
		server.hasStatement(collUri, RDF.TYPE, metaVocab.collectionClass)

	def fetchLite(collUri: IRI): Option[UriResource] = {
		if(collectionExists(collUri)) Some(
			UriResource(collUri.toJava, Some(getTitle(collUri)))
		) else None
	}

	def getParentCollections(dobj: IRI): Seq[UriResource] =
		server.getStatements(None, Some(memberProp), Some(dobj))
			.map(_.getSubject)
			.collect{case iri: IRI if getNextVersion(iri).isEmpty => fetchLite(iri)}
			.flatten
			.toIndexedSeq

	def getCreatorIfCollExists(hash: Sha256Sum): Option[IRI] = {
		val collUri = vocab.getCollection(hash)
		server.getUriValues(collUri, metaVocab.dcterms.creator, InstanceServer.AtMostOne).headOption
	}

	def collectionExists(coll: Sha256Sum): Boolean = collectionExists(vocab.getCollection(coll))
}

class CollectionFetcher(
	server: InstanceServer,
	plainFetcher: PlainStaticObjectFetcher,
	vocab: CpVocab
)(implicit envri: Envri) extends CollectionFetcherLite(server, vocab) {collFetcher =>

	def fetchStatic(hash: Sha256Sum): Option[StaticCollection] = {
		val collUri = vocab.getCollection(hash)
		if(collectionExists(collUri)) Some(getExistingStaticColl(collUri))
		else None
	}

	private def getExistingStaticColl(coll: IRI): StaticCollection = {
		val dct = metaVocab.dcterms

		val members = server.getUriValues(coll, memberProp).map{item =>
			if(collectionExists(item)) getExistingStaticColl(item)
			else plainFetcher.getPlainStaticObject(item)
		}.sortBy(_ match{
			case coll: StaticCollection => coll.title
			case dobj: PlainStaticObject => dobj.name
		})

		StaticCollection(
			res = coll.toJava,
			members = members,
			creator = getOrganization(getSingleUri(coll, dct.creator)),
			title = getTitle(coll),
			description = getOptionalString(coll, dct.description),
			nextVersion = getNextVersion(coll),
			previousVersion = getPreviousVersion(coll).flattenToSeq.headOption,
			doi = getOptionalString(coll, metaVocab.hasDoi)
		)
	}

}
