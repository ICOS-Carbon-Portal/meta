package se.lu.nateko.cp.meta

import scala.util.{Try, Success, Failure}

package object utils {

	implicit class ToTryConvertibleOption[T](val inner: Option[T]) extends AnyVal{
		def toTry(error: => Throwable): Try[T] = inner.map(Success.apply)
			.getOrElse(Failure(error))
	}

}
