package se.lu.nateko.cp.meta.upload

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalajs.dom.{ document, html }

object Utils {

	def getElement[T <: html.Element : ClassTag](id: String): Option[T] = document.getElementById(id) match{
		case input: T => Some(input)
		case _ => None
	}

	def whenDone[T](fut: Future[T])(cb: T => Unit): Future[T] = fut.andThen{
		case Success(res) => cb(res)
		case Failure(err) => println(err.getMessage)
	}

	def fail(msg: String) = Failure(new Exception(msg))

	implicit class TryWithErrorEnrichment[T](val inner: Try[T]) extends AnyVal{
		def withErrorContext(ctxt: String): Try[T] = inner.recoverWith{
			case err: Throwable => Failure(new Exception(ctxt + ": " + err.getMessage, err))
		}
	}
}
