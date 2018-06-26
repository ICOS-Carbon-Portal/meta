package se.lu.nateko.cp.meta.upload

import org.scalajs.dom.raw.File
import org.scalajs.dom.crypto.crypto
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import org.scalajs.dom.raw.FileReader
import scala.concurrent.Future
import scala.scalajs.js.typedarray.ArrayBuffer
import org.scalajs.dom.crypto.HashAlgorithm
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.typedarray.Int8Array
import scala.concurrent.Promise

object FileHasher {

	def hash(file: File): Future[Sha256Sum] = readFile(file)
		.flatMap{buff =>
			crypto.subtle.digest(HashAlgorithm.`SHA-256`, buff).toFuture
		}
		.asInstanceOf[Future[ArrayBuffer]]
		.map(ab => new Sha256Sum(new Int8Array(ab).toArray))


	def readFile(file: File): Future[ArrayBuffer] = {
		val p = Promise[ArrayBuffer]()
		val reader = new FileReader()
		reader.onload = e => {
			p.success(reader.result.asInstanceOf[ArrayBuffer])
		}
		reader.onerror = e => {
			p.failure(new Exception("Failed to read file"))
		}
		reader.readAsArrayBuffer(file)
		p.future
	}
}