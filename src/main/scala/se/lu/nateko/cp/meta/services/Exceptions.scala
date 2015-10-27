package se.lu.nateko.cp.meta.services

import scala.util.control.NoStackTrace

sealed abstract class ServiceException(val message: String) extends RuntimeException(
		if(message == null) "" else message
	) with NoStackTrace

final class UploadUserErrorException(message: String) extends ServiceException(message)
final class UnauthorizedUploadException(message: String) extends ServiceException(message)

final class UnauthorizedStationUpdateException(message: String) extends ServiceException(message)
final class UnauthorizedUserInfoUpdateException(message: String) extends ServiceException(message)

final class IllegalLabelingStatusException(status: String) extends ServiceException(s"Illegal labeling application status '$status'")
