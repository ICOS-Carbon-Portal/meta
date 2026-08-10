package se.lu.nateko.cp.meta.utils

import scala.language.unsafeNulls

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path.{Empty, Segment, Slash}

import java.time.Instant
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import scala.util.{Failure, Success, Try}

extension [T](inner: Option[T])
	def toTry(error: => Throwable): Try[T] = inner.map(Success.apply)
		.getOrElse(Failure(error))

extension[T](inner: Set[T])
	def containsEither(elems: T*): Boolean =
		elems.exists(inner.contains)

def urlEncode(s: String): String = Segment(s, Empty).toString

def urlDecode(s: String): String = Uri("/" + s).path match {
	case Slash(Segment(head, _)) => head
	case _ => s
}

def parseInstant(dateTimeIso: String): Instant = Instant.from(ISO_DATE_TIME.parse(dateTimeIso))

def getStackTrace(err: Throwable): String = {
	val traceWriter = new java.io.StringWriter()
	err.printStackTrace(new java.io.PrintWriter(traceWriter))
	traceWriter.toString
}

def parseJsonStringArray(s: String): Option[Array[String]] = {
	import spray.json.*
	import DefaultJsonProtocol.*
	try{
		Some(s.parseJson.convertTo[Array[String]])
	} catch{
		case _: Throwable => None
	}
}

def parseCommaSepList(s: String): Array[String] = s.split(",").map(_.trim).filter(!_.isEmpty)

def formatBytes(size: Long): String = {
	val k: Double = 1024
	val sizes = Seq("bytes", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
	val i: Double = Math.floor(Math.log(size.toDouble) / Math.log(k))
	val inBytes = if(i > 0) s" ($size bytes)" else ""

	s"${Math.round(size / Math.pow(k, i))} ${sizes(i.toInt)}$inBytes"
}
