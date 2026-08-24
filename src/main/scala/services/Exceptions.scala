package se.lu.nateko.cp.meta.services

import scala.language.unsafeNulls

/**
 * meta-only members of the metadata-domain error hierarchy: upload, labeling and authorization
 * failures that only meta's routing layer raises and interprets. The base `ServiceException` and
 * the shared `MetadataException` live in rdf-common, in this same package.
 *
 * Unrelated to `src/main/scala/ingestion/Exceptions.scala`; do not merge the two.
 */
final class UploadUserErrorException(message: String) extends ServiceException(message)
final class UnauthorizedUploadException(message: String) extends ServiceException(message)

final class UnauthorizedStationUpdateException(message: String) extends ServiceException(message)
final class UnauthorizedUserInfoUpdateException(message: String) extends ServiceException(message)

final class IllegalLabelingStatusException(message: String) extends ServiceException(message)
