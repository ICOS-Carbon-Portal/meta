package se.lu.nateko.cp.meta.upload

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Failure
import scala.util.Success

import org.scalajs.dom.Element
import org.scalajs.dom.document
import org.scalajs.dom.raw.HTMLInputElement

object UploadApp {

	def main(args: Array[String]): Unit = {

		val input = getElement[HTMLInputElement]("fileinput").get

		input.oninput = e => {
			val file = input.files(0)

			whenDone(FileHasher.hash(file)){hash =>
				println(file.name)
				println(hash.hex)
				println(hash.base64Url)
			}
		}

		whenDone(Backend.sitesStationInfo)(_ foreach println)
		whenDone(Backend.getSitesObjSpecs)(_ foreach println)
		whenDone(Backend.submitterIds)(_ foreach println)
	}

	def getElement[T <: Element : ClassTag](id: String): Option[T] = document.getElementById(id) match{
		case input: T => Some(input)
		case _ => None
	}

	def whenDone[T](fut: Future[T])(cb: T => Unit): Future[T] = fut.andThen{
		case Success(res) => cb(res)
		case Failure(err) => println(err.getMessage)
	}
}
