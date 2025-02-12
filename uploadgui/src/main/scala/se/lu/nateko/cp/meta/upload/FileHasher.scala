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

	val FileMaxSize = 2_000_000_000
	val FileMaxSizeMessage = "This file could be too large to upload with this web app. " +
		"Please consider scripted uploads as instructed here: " +
		"https://github.com/ICOS-Carbon-Portal/meta/?tab=readme-ov-file#upload-instructions-scripting"

	def hash(file: File): Future[Sha256Sum] = readFile(file)
		.flatMap{buff =>
			crypto.subtle.digest(HashAlgorithm.`SHA-256`, buff).toFuture
		}
		.asInstanceOf[Future[ArrayBuffer]]
		.map(ab => new Sha256Sum(new Int8Array(ab).toArray))
		.transform(
			identity,
			err =>
				if file.size > FileMaxSize
				then new Exception(FileMaxSizeMessage, err)
				else err
		)

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
