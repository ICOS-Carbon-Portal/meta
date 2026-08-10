package se.lu.nateko.cp.meta.utils.async

import scala.concurrent.Future
import scala.util.control.NoStackTrace

/** Used only by rdfStore's `CitationClient`, where DOI-lookup failures are expected and frequent
  * enough that stack traces are pure noise. */
def errorLite[T](msg: String): Future[T] = Future.failed(new Exception(msg) with NoStackTrace)
