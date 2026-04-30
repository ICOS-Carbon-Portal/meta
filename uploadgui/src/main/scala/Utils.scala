package se.lu.nateko.cp.meta.upload

import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.scalajs.dom.{document, html}
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.Element

object Utils {


	def getElementById[T <: html.Element : ClassTag](id: String): Option[T] = document.getElementById(id) match{
		case input: T => Some(input)
		case _ => None
	}

	def querySelector[T <: html.Element : ClassTag](parent: html.Element, selector: String): Option[T] = parent.querySelector(selector) match {
		case element: T => Some(element)
		case _ => None
	}

	def querySelectorAll[T <: html.Element : ClassTag](parent: html.Element, selector: String): Seq[T] = parent.querySelectorAll(selector).collect{
		case element: T => element
	}.toIndexedSeq

	def deepClone[T <: html.Element](elem: T): T = elem.cloneNode(true).asInstanceOf[T]

	def fail(msg: String) = Failure(new Exception(msg))

	def initializeBootstrapPopover(elem: Element): Popover = js.Dynamic.newInstance(js.Dynamic.global.bootstrap.Popover)(elem).asInstanceOf[Popover]

	def initAllBootstrapPopovers(): Unit = dom.document
		.querySelectorAll("[data-bs-toggle='popover']")
		.foreach(initializeBootstrapPopover)

	extension [T](inner: Try[T])
		def withErrorContext(ctxt: String): Try[T] = inner.recoverWith{
			case err: Throwable => Failure(new Exception(ctxt + ": " + err.getMessage, err))
		}

	extension [T](inner: Option[T])
		def withMissingError(msg: String): Try[T] = inner.fold[Try[T]]{Failure(new Exception(msg))}(Success(_))

	@js.native
	trait Popover extends js.Object:
		def disable(): Unit = js.native
		def enable(): Unit = js.native
		def hide(): Unit = js.native
		def show(): Unit = js.native
		def update(): Unit = js.native
		def dispose(): Unit = js.native

}
