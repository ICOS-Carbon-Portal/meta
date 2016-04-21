package se.lu.nateko.cp.meta.ingestion

import scala.util.control.NoStackTrace

sealed class IngestionException(val message: String) extends RuntimeException(
		if(message == null) "" else message
	) with NoStackTrace

class BadmFormatException(msg: String) extends IngestionException(msg)
