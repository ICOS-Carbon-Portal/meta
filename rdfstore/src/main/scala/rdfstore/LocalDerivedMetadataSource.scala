package se.lu.nateko.cp.meta.rdfstore

import java.net.URI
import scala.concurrent.Future
import scala.util.Try

/** Derived metadata from the RDF store running in the same process. Resolves synchronously in the calling thread,
  * like the landing page reads did before derived metadata was fetched separately.
  */
final class LocalDerivedMetadataSource(service: DerivedMetadataService) extends DerivedMetadataSource:

	def resolve(resources: Seq[URI]): Future[DerivedMetadataResponse] =
		Future.fromTry(Try(service.resolve(resources)))

	def dropDoiCache(doi: String): Future[Unit] =
		if service.dropDoiCache(doi) then Future.unit
		else Future.failed(IllegalArgumentException(s"Invalid DOI: $doi"))
