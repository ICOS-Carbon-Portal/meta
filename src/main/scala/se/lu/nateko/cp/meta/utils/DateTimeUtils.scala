package se.lu.nateko.cp.meta.utils

import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone

object DateTimeUtils {

	val defaultFormatter: DateTimeFormatter =
		ISODateTimeFormat.dateTimeNoMillis.withZone(DateTimeZone.UTC)

	def defaultNowString: String = defaultFormatter.print(new org.joda.time.DateTime())
}