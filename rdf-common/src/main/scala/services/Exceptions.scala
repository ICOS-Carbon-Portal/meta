package se.lu.nateko.cp.meta.services

import scala.language.unsafeNulls

import scala.util.control.NoStackTrace

sealed class ServiceException(val message: String) extends RuntimeException(
		if(message == null) "" else message
	) with NoStackTrace

final class UploadUserErrorException(message: String) extends ServiceException(message)
final class UnauthorizedUploadException(message: String) extends ServiceException(message)
final class UploadCompletionException(message: String) extends ServiceException(message)
final class MetadataException(message: String) extends ServiceException(message)

final class UnauthorizedStationUpdateException(message: String) extends ServiceException(message)
final class UnauthorizedUserInfoUpdateException(message: String) extends ServiceException(message)

final class IllegalLabelingStatusException(message: String) extends ServiceException(message)

final class PidMintingException(message: String) extends ServiceException(message)

object CacheSizeLimitExceeded extends ServiceException("Cache size limit exceeded.")
