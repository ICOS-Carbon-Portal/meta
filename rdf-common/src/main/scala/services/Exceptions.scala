package se.lu.nateko.cp.meta.services

import scala.language.unsafeNulls

import scala.util.control.NoStackTrace

class ServiceException(val message: String) extends RuntimeException(
		if(message == null) "" else message
	) with NoStackTrace

final class MetadataException(message: String) extends ServiceException(message)
