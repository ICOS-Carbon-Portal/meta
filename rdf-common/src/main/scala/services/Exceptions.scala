package se.lu.nateko.cp.meta.services

import scala.language.unsafeNulls

import scala.util.control.NoStackTrace

/**
 * The base metadata-domain error, and the one subtype both applications raise.
 *
 * Deliberately not `sealed`: `meta` defines the rest of the hierarchy in its own
 * `src/main/scala/services/Exceptions.scala` (same package), since the upload, labeling and
 * authorization errors are meaningful only to meta's routing layer. `rdfStore` raises
 * `MetadataException` alone.
 */
class ServiceException(val message: String) extends RuntimeException(
		if(message == null) "" else message
	) with NoStackTrace

final class MetadataException(message: String) extends ServiceException(message)
