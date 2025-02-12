package se.lu.nateko.cp.meta.upload

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js.typedarray.Int8Array

import org.scalajs.dom.HashAlgorithm
import org.scalajs.dom.crypto
import org.scalajs.dom.File
import org.scalajs.dom.FileReader

import scala.util.{Failure, Success}

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

object FileHasher {

	val FileMaxSize = 2000000000
	val FileMaxSizeMessage = "This file could be too large to upload with this form. " +
		"Please contact us if you need an alternative."

	def hash(file: File): Future[Sha256Sum] = readFile(file)
		.flatMap{buff =>
			crypto.subtle.digest(HashAlgorithm.`SHA-256`, buff).toFuture
		}
		.asInstanceOf[Future[ArrayBuffer]]
		.map(ab => new Sha256Sum(new Int8Array(ab).toArray))
		.transform{
			case Success(hashSum) => Success(hashSum)
			case Failure(err) if file.size > FileMaxSize =>
				throw new Exception(FileMaxSizeMessage)
			case Failure(err) => throw err
		}

	def readFile(file: File): Future[ArrayBuffer] = {

		val p = Promise[ArrayBuffer]()

		val reader = new FileReader()

		reader.onload = _ => {
			p.success(reader.result.asInstanceOf[ArrayBuffer])
		}

		reader.onerror = _ => {
			p.failure(new Exception(reader.error.toLocaleString()))
		}

		reader.readAsArrayBuffer(file)

		p.future
	}
}
