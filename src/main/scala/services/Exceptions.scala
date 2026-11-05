package se.lu.nateko.cp.meta.services

import scala.language.unsafeNulls

final class UploadUserErrorException(message: String) extends ServiceException(message)
final class UnauthorizedUploadException(message: String) extends ServiceException(message)

final class UnauthorizedStationUpdateException(message: String) extends ServiceException(message)
final class UnauthorizedUserInfoUpdateException(message: String) extends ServiceException(message)

final class IllegalLabelingStatusException(message: String) extends ServiceException(message)
