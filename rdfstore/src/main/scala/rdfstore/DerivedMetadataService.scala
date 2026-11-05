package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import se.lu.nateko.cp.meta.services.citation.CitationProvider

import java.net.URI

final class DerivedMetadataService private (
	factory: ValueFactory,
	resolveIri: IRI => DerivedMetadataResult,
	dropCache: String => Boolean
):

	def resolve(resource: IRI): DerivedMetadataResult = resolveIri(resource)

	private[rdfstore] def resolve(resources: Seq[URI]): DerivedMetadataResponse =
		val results = resources.map: uri =>
			try resolve(factory.createIRI(uri.toString))
			catch case err: IllegalArgumentException =>
				DerivedMetadataResult(uri, "invalid", None)
		DerivedMetadataResponse(version = 1, results)

	private[rdfstore] def dropDoiCache(doi: String): Boolean = dropCache(doi)

object DerivedMetadataService:
	def apply(citations: CitationProvider): DerivedMetadataService =
		new DerivedMetadataService(citations.metaVocab.factory, resource =>
			val uri = new URI(resource.stringValue)
			val found = citations.getReferencesWithRemoteCitation(resource)
			found.result match
				case None => DerivedMetadataResult(uri, "notFound", None)
				case Some((references, remoteCitation)) =>
					val citation = citations.getCitation(resource).orElse(references.citationString)
					val licence = citations.getLicence(resource).orElse(references.licence)
					val metadata = DerivedMetadata(uri, references, citation, licence, found.errors, Some(remoteCitation))
					DerivedMetadataResult(uri, "ready", Some(metadata))
		, doi =>
			se.lu.nateko.cp.doi.Doi.parse(doi).toOption.exists: parsed =>
				citations.doiCiter.dropCache(parsed)
				true
		)

	/** Derived metadata is not available for any resource */
	def unavailable(factory: ValueFactory): DerivedMetadataService =
		new DerivedMetadataService(factory, resource =>
			DerivedMetadataResult(new URI(resource.stringValue), "unavailable", None)
		, _ => false
		)

	/** Derived metadata is ready for the given resources only */
	def fixed(factory: ValueFactory, values: Map[URI, DerivedMetadata]): DerivedMetadataService =
		new DerivedMetadataService(factory, resource =>
			val uri = new URI(resource.stringValue)
			values.get(uri) match
				case Some(metadata) => DerivedMetadataResult(uri, "ready", Some(metadata))
				case None => DerivedMetadataResult(uri, "notFound", None)
		, _ => false
		)
