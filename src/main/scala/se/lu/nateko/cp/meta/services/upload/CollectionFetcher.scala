package se.lu.nateko.cp.meta.services.upload

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.IRI

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper

class CollectionFetcher(
	protected val server: InstanceServer,
	dobjsServer: InstanceServer,
	protected val vocab: CpVocab,
	protected val metaVocab: CpmetaVocab
)(implicit envri: Envri) extends CpmetaFetcher {collFetcher =>

	private val dobjFetcher = new CpmetaFetcher{
		val server = dobjsServer
		val vocab = collFetcher.vocab
		val metaVocab = collFetcher.metaVocab
	}

	def fetchStatic(hash: Sha256Sum): Option[StaticCollection] = {
		val collUri = vocab.getCollection(hash)
		if(collectionExists(collUri)) Some(getExistingStaticColl(collUri))
		else None
	}

	private def collectionExists(collUri: IRI): Boolean =
		server.hasStatement(collUri, RDF.TYPE, metaVocab.collectionClass)

	private def getExistingStaticColl(coll: IRI): StaticCollection = {
		val dct = metaVocab.dcterms

		val members = server.getUriValues(coll, dct.hasPart).map{item =>
			if(collectionExists(item)) getExistingStaticColl(item)
			else dobjFetcher.getPlainDataObject(item)
		}.sortBy(_ match{
			case coll: StaticCollection => coll.title
			case dobj: PlainDataObject => dobj.name
		})

		StaticCollection(
			res = coll.toJava,
			members = members,
			creator = getOrganization(getSingleUri(coll, dct.creator)),
			title = getSingleString(coll, dct.title),
			description = getOptionalString(coll, dct.description)
		)
	}

}
