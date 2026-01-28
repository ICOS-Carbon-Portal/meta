package se.lu.nateko.cp.meta.test

import scala.language.unsafeNulls

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters.ListHasAsScala

object LogCapture:
	def captureLogs[T](loggerClass: Class[?])(block: => T): (T, Seq[ILoggingEvent]) =
		val logger = LoggerFactory.getLogger(loggerClass).asInstanceOf[Logger]
		val appender = new ListAppender[ILoggingEvent]()
		appender.start()
		logger.addAppender(appender)
		try
			val result = block
			(result, appender.list.asScala.toSeq)
		finally
			logger.detachAppender(appender)
