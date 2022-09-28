package se.lu.nateko.cp.meta.utils

import java.time.Instant
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import scala.util.{Try, Success, Failure}
import scala.reflect.ClassTag
import java.nio.charset.StandardCharsets
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path.{Segment, Slash, Empty}
import scala.collection.mutable.Buffer

extension [T](inner: Option[T])
	def toTry(error: => Throwable): Try[T] = inner.map(Success.apply)
		.getOrElse(Failure(error))

def transformEither[L0, R0, L, R](left: L0 => L, right: R0 => R)(either: Either[L0, R0]): Either[L, R] =
	either.fold[Either[L, R]](l => Left(left(l)), r => Right(right(r)))

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

extension [T](item: Option[Either[T, Seq[T]]])
	def flattenToSeq: Seq[T] = item.fold(Seq.empty[T]){either =>
		either.fold(Seq(_), identity)
	}

extension (inner: AnyRef)
	def asOptInstanceOf[T: ClassTag]: Option[T] = inner match{
		case t: T => Some(t)
		case _ => None
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

def printAsJsonArray(ss: Seq[String]): String = {
	import spray.json.{JsArray, JsString}
	JsArray(ss.map(s => JsString(s)).toVector).prettyPrint
}

def parseCommaSepList(s: String): Array[String] = s.split(",").map(_.trim).filter(!_.isEmpty)

def slidingByKey[T >: Null, K](inner: Iterator[T])(key: T => K) = new Iterator[IndexedSeq[T]]{
	private val group = Buffer.empty[T]

	def hasNext: Boolean = !group.isEmpty || {
		if(inner.hasNext) {
			group.append(inner.next())
			true
		}
		else false
	}

	def next(): IndexedSeq[T] =
		if(!hasNext)
			throw new NoSuchElementException("slidingByKey iterator empty")
		else {
			val lastKey = key(group.last)
			var next: T = null
			while(inner.hasNext && {next = inner.next(); key(next) == lastKey}){
				group.append(next)
				next = null
			}
			val nextGroup = group.toIndexedSeq
			group.clear()
			if(next != null) group.append(next)
			nextGroup
		}

}

def formatBytes(size: Long): String = {
	val k: Double = 1024
	val sizes = Seq("bytes", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
	val i: Double = Math.floor(Math.log(size.toDouble) / Math.log(k))
	val inBytes = if(i > 0) s" ($size bytes)" else ""

	s"${Math.round(size / Math.pow(k, i))} ${sizes(i.toInt)}$inBytes"
}
