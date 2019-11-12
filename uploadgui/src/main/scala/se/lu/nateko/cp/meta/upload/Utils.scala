package se.lu.nateko.cp.meta.upload

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.scalajs.dom.{document, html}

object Utils {

	private val progressBar = new HtmlElements("#progress-bar")
	private val alert = getElementById[html.Div]("alert-placeholder").get

	def getElementById[T <: html.Element : ClassTag](id: String): Option[T] = document.getElementById(id) match{
		case input: T => Some(input)
		case _ => None
	}

	def querySelector[T <: html.Element : ClassTag](parent: html.Element, selector: String): Option[T] = parent.querySelector(selector) match {
		case element: T => Some(element)
		case _ => None
	}

	def whenDone[T](fut: Future[T])(cb: T => Unit): Future[T] = fut.andThen{
		case Success(res) => cb(res)
		case Failure(err) =>
			showAlert(err.getMessage, "alert alert-danger")
	}

	def showAlert(message: String, alertType: String): Unit = {
		alert.setAttribute("class", alertType)
		alert.innerHTML = message
	}

	def hideAlert(): Unit = {
		alert.setAttribute("class", "")
		alert.innerHTML = ""
	}

	def showProgressBar(): Unit = {
		progressBar.show()
	}

	def hideProgressBar(): Unit = {
		progressBar.hide()
	}

	def fail(msg: String) = Failure(new Exception(msg))

	implicit class TryWithErrorEnrichment[T](val inner: Try[T]) extends AnyVal{
		def withErrorContext(ctxt: String): Try[T] = inner.recoverWith{
			case err: Throwable => Failure(new Exception(ctxt + ": " + err.getMessage, err))
		}
	}

	implicit class OptionWithMissingErrorEnrichment[T](val inner: Option[T]) extends AnyVal{
		def withMissingError(msg: String): Try[T] = inner.fold[Try[T]]{Failure(new Exception(msg))}(Success(_))
	}
}
