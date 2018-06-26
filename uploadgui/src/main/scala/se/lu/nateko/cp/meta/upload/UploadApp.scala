package se.lu.nateko.cp.meta.upload

import org.scalajs.dom.document
import org.scalajs.dom.raw.HTMLInputElement
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom.Element
import scala.reflect.ClassTag

object UploadApp {

	def main(args: Array[String]): Unit = {

		val input = getElement[HTMLInputElement]("fileinput").get

		input.oninput = e => {
			val file = input.files(0)
			val hashFut = FileHasher.hash(file)
			hashFut.foreach(hash => {
				println(file.name)
				println(hash.hex)
				println(hash.base64Url)
			})
			hashFut.failed foreach println
		}
	}

	def getElement[T <: Element : ClassTag](id: String): Option[T] = document.getElementById(id) match{
		case input: T => Some(input)
		case _ => None
	}
}
