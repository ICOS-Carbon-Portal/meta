package se.lu.nateko.cp.meta.upload

import org.scalajs.dom.window.document
import org.scalajs.dom.raw.HTMLInputElement
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object UploadApp {
	def main(args: Array[String]): Unit = {
		val input = document.getElementById("fileinput").asInstanceOf[HTMLInputElement]

		input.oninput = e => {
			val file = input.files(0)
			val hashFut = FileHasher.hash(file)
			hashFut.foreach(hash => println(hash.id))
			hashFut.failed foreach println
		}
	}

}