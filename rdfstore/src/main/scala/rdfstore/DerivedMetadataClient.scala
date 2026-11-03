package se.lu.nateko.cp.meta.rdfstore

import se.lu.nateko.cp.meta.core.data.{DataObject, DocObject, StaticCollection, StaticObject}
import se.lu.nateko.cp.meta.services.citation.RemoteCitation

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

/** Where meta gets the metadata derived by the RDF store (references, citations, licences) from */
trait DerivedMetadataSource:
	def resolve(resources: Seq[URI]): Future[DerivedMetadataResponse]
	def dropDoiCache(doi: String): Future[Unit]

/** An item enriched with derived metadata, the status of its citation from a DOI or an external repository,
  * and the warnings from making the citation
  */
case class WithRemoteCitation[+T](item: T, remoteCitation: RemoteCitation, warnings: Seq[String] = Nil)

final class DerivedMetadataClient(source: DerivedMetadataSource)(using ExecutionContext):

	def resolve(resources: Seq[URI]): Future[DerivedMetadataResponse] = source.resolve(resources)

	def resolve(resource: URI): Future[DerivedMetadataResult] =
		resolve(Seq(resource)).map(_.results.headOption.getOrElse(DerivedMetadataResult(resource, "notFound", None)))

	def enrich(resource: URI, item: StaticObject): Future[StaticObject] =
		enrichWithRemoteCitation(resource, item).map(_.item)

	def enrich(resource: URI, item: StaticCollection): Future[StaticCollection] =
		enrichWithRemoteCitation(resource, item).map(_.item)

	def enrichWithRemoteCitation(resource: URI, item: StaticObject): Future[WithRemoteCitation[StaticObject]] =
		enrichWith(resource, item): metadata =>
			item match
				case data: DataObject => data.copy(references = metadata.references)
				case doc: DocObject => doc.copy(references = metadata.references)

	def enrichWithRemoteCitation(resource: URI, item: StaticCollection): Future[WithRemoteCitation[StaticCollection]] =
		enrichWith(resource, item)(metadata => item.copy(references = metadata.references))

	private def enrichWith[T](resource: URI, item: T)(enrich: DerivedMetadata => T): Future[WithRemoteCitation[T]] =
		resolve(resource).map:
			case DerivedMetadataResult(_, "ready", Some(metadata)) =>
				WithRemoteCitation(enrich(metadata), metadata.remoteCitation.getOrElse(RemoteCitation.NotApplicable), metadata.warnings)
			case _ => WithRemoteCitation(item, RemoteCitation.NotApplicable)

	def dropDoiCache(doi: String): Future[Unit] = source.dropDoiCache(doi)
