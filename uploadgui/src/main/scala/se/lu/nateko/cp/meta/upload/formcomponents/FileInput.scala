package se.lu.nateko.cp.meta.upload.formcomponents

// HTMLElement from scalajs is not written with explicit-null in mind,
// and we pass `null` to onchange handler.
import scala.language.unsafeNulls

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.{Try, Success}
import org.scalajs.dom
import org.scalajs.dom.html
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.upload.FileHasher
import se.lu.nateko.cp.meta.upload.Utils.*
import se.lu.nateko.cp.meta.upload.UploadApp

class FileInput(elemId: String, cb: () => Unit){
	private val fileInput = getElementById[html.Input](elemId)
	private var _hash: Try[Sha256Sum] = file.flatMap(_ => fail("hashsum computing has not started yet"))
	private var _lastModified: Double = 0

	def file: Try[dom.File] = if(fileInput.files.length > 0) Success(fileInput.files(0)) else fail("no file chosen")

	def hash: Try[Sha256Sum] = {
		if (hasBeenModified) {
			fail("The file has been modified, please choose the updated version")
		} else {
			_hash
		}
	}

	def hasBeenModified: Boolean =
		file.map(getLastModified(_)) != Success(_lastModified)

	def rehash(): Future[Unit] = for(f <- Future.fromTry(file); hash <- FileHasher.hash(f)) yield{
		_hash = Success(hash)
		_lastModified = getLastModified(f)
	}

	// The event is not dispatched if the file selected is the same as before
	fileInput.onchange = _ => file.foreach{f =>
		_hash = fail("hashsum is being computed")
		_lastModified = getLastModified(f)
		cb()

		if(f.size > 2_000_000_000) {
			UploadApp.showAlert("The file you selected is too large to upload with this form. " +
				"Please use scripted uploads as described in " +
				"<a class='alert-link' href='https://github.com/ICOS-Carbon-Portal/meta/?tab=readme-ov-file#upload-instructions-scripting'>our documentation</a>."
				, "alert alert-danger")
			_hash = fail("The file is too large")
			_lastModified = getLastModified(f)
			cb()
		} else {
			UploadApp.hideAlert()

			UploadApp.whenDone(FileHasher.hash(f)){hash =>
				if(file.toOption.contains(f)) {
					_hash = Success(hash) //file could have been changed while digesting for SHA-256
					_lastModified = getLastModified(f)
					cb()
				}
			}
		}
	}

	def enable(): Unit = {
		fileInput.disabled = false
	}

	def disable(): Unit = {
		fileInput.disabled = true
	}

	if(file.isSuccess){//pre-chosen file, e.g. due to browser page reload
		queue.execute(() => fileInput.onchange(null))// no need to do this eagerly, just scheduling
	}

	private def getLastModified(file: dom.File) =
		file.asInstanceOf[js.Dynamic].lastModified.asInstanceOf[Double]

}
