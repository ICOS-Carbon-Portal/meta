package se.lu.nateko.cp.meta.services.derived

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import se.lu.nateko.cp.meta.services.citation.CitationProvider

import java.net.URI

/**
 * Store-owned facade for values exposed as virtual RDF triples.  Both the SPARQL Sail adapter
 * and the HTTP route use this facade so there is exactly one derivation path and one DOI cache.
 */
final class DerivedMetadataService private (
	private val factory: ValueFactory,
	private val resolveIri: IRI => DerivedMetadataResult,
	private val dropCache: String => Boolean
):

	def resolve(resource: IRI): DerivedMetadataResult = resolveIri(resource)

	def resolve(resources: Seq[URI]): DerivedMetadataResponse =
		val results = resources.map: uri =>
			try resolve(factory.createIRI(uri.toString))
			catch case err: IllegalArgumentException =>
				DerivedMetadataResult(uri, "invalid", None)
		DerivedMetadataResponse(version = 1, results)

	def dropDoiCache(doi: String): Boolean = dropCache(doi)

object DerivedMetadataService:
	def apply(citations: CitationProvider): DerivedMetadataService =
		new DerivedMetadataService(citations.metaVocab.factory, resource =>
			val uri = new URI(resource.stringValue)
			citations.getReferences(resource) match
				case None => DerivedMetadataResult(uri, "notFound", None)
				case Some(references) =>
					val citation = citations.getCitation(resource).orElse(references.citationString)
					val licence = citations.getLicence(resource).orElse(references.licence)
					DerivedMetadataResult(uri, "ready", Some(DerivedMetadata(uri, references, citation, licence)))
		, doi =>
			se.lu.nateko.cp.doi.Doi.parse(doi).toOption.exists: parsed =>
				citations.doiCiter.dropCache(parsed)
				true
		)

	def unavailable(factory: ValueFactory): DerivedMetadataService =
		new DerivedMetadataService(factory, resource =>
			DerivedMetadataResult(new URI(resource.stringValue), "unavailable", None)
		, _ => false
		)
