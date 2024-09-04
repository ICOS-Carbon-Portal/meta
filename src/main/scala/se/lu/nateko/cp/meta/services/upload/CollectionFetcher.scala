package se.lu.nateko.cp.meta.services.upload

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.api.RdfLens.CollConn
import se.lu.nateko.cp.meta.api.RdfLens.DocConn
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import se.lu.nateko.cp.meta.utils.*
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI


class CollectionReader(val metaVocab: CpmetaVocab, citer: CitableItem => References) extends CpmetaReader:

	import metaVocab.{dcterms => dct}

	private def getCollTitle(collUri: IRI)(using CollConn): Validated[String] = getSingleString(collUri, dct.title)

	def collectionExists(collUri: IRI)(using CollConn): Boolean = resourceHasType(collUri, metaVocab.collectionClass)

	def getCreatorIfCollExists(collIri: IRI)(using CollConn): Validated[Option[IRI]] = getOptionalUri(collIri, dct.creator)

	def fetchCollLite(collUri: IRI)(using CollConn): Validated[UriResource] =
		if collectionExists(collUri) then
			getCollTitle(collUri).map: title =>
				UriResource(collUri.toJava, Some(title), Nil)
		else Validated.error("collection does not exist")

	def getParentCollections(dobj: IRI)(using CollConn): Validated[Seq[UriResource]] =
		val allParentColls = getPropValueHolders(dct.hasPart, dobj).toIndexedSeq

		val deprecatedSet = allParentColls.flatMap(getPreviousVersions).toSet

		Validated.sequence(allParentColls.filterNot(deprecatedSet.contains).map(fetchCollLite))

	def fetchStaticColl(collUri: IRI, hashOpt: Option[Sha256Sum])(using CollConn, DocConn): Validated[StaticCollection] =
		if !collectionExists(collUri) then Validated.error(s"Collection $collUri does not exist")
		else getExistingStaticColl(collUri, hashOpt)

	def fetchCollCoverage(collUri: IRI)(using CollConn): Validated[Option[GeoFeature]] =
		getOptionalUri(collUri, metaVocab.hasSpatialCoverage).flatMap: covUriOpt =>
			covUriOpt.map(covUri => getCoverage(covUri)).sinkOption

	private def getExistingStaticColl(
		coll: IRI, hashOpt: Option[Sha256Sum] = None
	)(using collConn: CollConn, docConn: DocConn): Validated[StaticCollection] =

		val membersV = Validated.sequence:
			getUriValues(coll, dct.hasPart)(using collConn).map: item =>
				if collectionExists(item) then getExistingStaticColl(item)
				else getPlainDataObject(item)(using RdfLens.global(using docConn))

		for
			creatorUri <- getSingleUri[CollConn](coll, dct.creator)
			members <- membersV
			creator <- getOrganization(creatorUri)(using collConn)
			title <- getCollTitle(coll)
			description <- getOptionalString[CollConn](coll, dct.description)
			doi <- getOptionalString[CollConn](coll, metaVocab.hasDoi)
			documentationUriOpt <- getOptionalUri[CollConn](coll, RDFS.SEEALSO)
			documentation <- documentationUriOpt.map(getPlainDocObject).sinkOption
			coverage <- fetchCollCoverage(coll)
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
				nextVersion = getNextVersionAsUri(coll)(using collConn),
				latestVersion = getLatestVersion(coll)(using collConn),
				previousVersion = getPreviousVersions(coll)(using collConn).headOption.map(_.toJava),
				doi = doi,
				documentation = documentation,
				coverage = coverage,
				references = References.empty
			)
			//TODO Consider adding collection-specific logic for licence information
			val refs = citer(init).copy(title = Some(init.title))
			val bestGeoCov = init.coverage.orElse:
				for
					doi <- refs.doi
					geos <- doi.geoLocations
					cov <- DataCite.geosToCp(geos)
				yield cov
			init.copy(coverage = bestGeoCov, references = refs)

end CollectionReader
