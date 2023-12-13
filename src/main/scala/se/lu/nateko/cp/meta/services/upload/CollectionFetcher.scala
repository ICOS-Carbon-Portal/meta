package se.lu.nateko.cp.meta.services.upload

import java.net.URI

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.IRI

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*

import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.utils.*
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import eu.icoscp.envri.Envri


class CollectionReader(val metaVocab: CpmetaVocab, citer: CitableItem => References) extends CpmetaReader:

	import metaVocab.{dcterms => dct}

	private def getCollTitle(collUri: IRI): TSC2V[String] = getSingleString(collUri, dct.title)

	def collectionExists(collUri: IRI): TSC2[Boolean] = resourceHasType(collUri, metaVocab.collectionClass)

	def getCreatorIfCollExists(collIri: IRI): TSC2V[Option[IRI]] = getOptionalUri(collIri, dct.creator)

	def fetchCollLite(collUri: IRI): TSC2V[UriResource] =
		if collectionExists(collUri) then
			getCollTitle(collUri).map: title =>
				UriResource(collUri.toJava, Some(title), Nil)
		else Validated.error("collection does not exist")

	def getParentCollections(dobj: IRI): TSC2V[Seq[UriResource]] =
		val allParentColls = getStatements(None, Some(dct.hasPart), Some(dobj))
			.map(_.getSubject)
			.collect{case iri: IRI => iri}
			.toIndexedSeq

		val deprecatedSet = allParentColls.flatMap(getPreviousVersions).toSet

		Validated.sequence(allParentColls.filterNot(deprecatedSet.contains).map(fetchCollLite))

	def fetchStaticColl(collUri: IRI, hashOpt: Option[Sha256Sum]): TSC2V[StaticCollection] =
		if !collectionExists(collUri) then Validated.error(s"Collection $collUri does not exist")
		else getExistingStaticColl(collUri, hashOpt)

	private def getExistingStaticColl(coll: IRI, hashOpt: Option[Sha256Sum] = None): TSC2V[StaticCollection] =

		val membersV = Validated.sequence:
			getUriValues(coll, dct.hasPart).map: item =>
				if collectionExists(item) then getExistingStaticColl(item)
				else getPlainStaticObject(item)(using globalLens)

		for
			creatorUri <- getSingleUri(coll, dct.creator)
			members <- membersV
			creator <- getOrganization(creatorUri)
			title <- getCollTitle(coll)
			description <- getOptionalString(coll, dct.description)
			doi <- getOptionalString(coll, metaVocab.hasDoi)
			documentationUriOpt <- getOptionalUri(coll, RDFS.SEEALSO)
			documentation <- documentationUriOpt.map(getPlainStaticObject).sinkOption
		yield
			val init = StaticCollection(
				res = coll.toJava,
				hash = hashOpt.getOrElse(Sha256Sum.fromBase64Url(coll.getLocalName).get),
				members = members.sortBy:
					case coll: StaticCollection => coll.title
					case dobj: PlainStaticObject => dobj.name
				,
				creator = creator,
				title = title,
				description = description,
				nextVersion = getNextVersionAsUri(coll),
				latestVersion = getLatestVersion(coll),
				previousVersion = getPreviousVersion(coll).flattenToSeq.headOption.map(_.toJava),
				doi = doi,
				documentation = documentation,
				references = References.empty
			)
			//TODO Consider adding collection-specific logic for licence information
			init.copy(references = citer(init).copy(title = Some(init.title)))

end CollectionReader
