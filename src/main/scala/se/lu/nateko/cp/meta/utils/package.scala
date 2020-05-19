package se.lu.nateko.cp.meta

import java.time.Instant
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import scala.util.{Try, Success, Failure}
import scala.reflect.ClassTag

package object utils {


	implicit class ToTryConvertibleOption[T](val inner: Option[T]) extends AnyVal{
		def toTry(error: => Throwable): Try[T] = inner.map(Success.apply)
			.getOrElse(Failure(error))
	}

	def urlEncode(s: String): String = {
		new java.net.URI(null, null, "/" + s, null).toASCIIString.substring(1)
	}

	def parseInstant(dateTimeIso: String): Instant = Instant.from(ISO_DATE_TIME.parse(dateTimeIso))

	def getStackTrace(err: Throwable): String = {
		val traceWriter = new java.io.StringWriter()
		err.printStackTrace(new java.io.PrintWriter(traceWriter))
		traceWriter.toString
	}

	implicit class OptionalItemOrSeqOps[T](val item: Option[Either[T, Seq[T]]]) extends AnyVal{
		def flattenToSeq: Seq[T] = item.fold(Seq.empty[T]){either =>
			either.fold(Seq(_), identity)
		}
	}

	implicit class AnyRefWithSafeOptTypecast(val inner: AnyRef) extends AnyVal{
		def asOptInstanceOf[T: ClassTag]: Option[T] = inner match{
			case t: T => Some(t)
			case _ => None
		}
	}

	def parseJsonStringArray(s: String): Option[Array[String]] = {
		import spray.json._
		import DefaultJsonProtocol._
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

}
