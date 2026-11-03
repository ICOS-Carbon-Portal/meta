package se.lu.nateko.cp.meta.services.upload.validation

import scala.language.unsafeNulls

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.test.services.upload.UploadTestFixture.{given, *}

import scala.util.Try

class ScopedValidatorTests extends AnyFunSpec{
	private def assertUserError(result: Try[?], message: String) = {
		assert(result.failed.get.isInstanceOf[UploadUserErrorException])
		assert(result.failed.get.getMessage.contains(message))
	}

	it("rejects duplicate filenames unless allowed, replaced, or held by self, unfinished, or deprecated objects"){
		withStore{ s =>
			import s.*
			val validator = ScopedValidator(vocab, meta)
			loadFixture("validation", "filename-taken")
			accessGlobal{
				assertUserError(validator.validateFileName(document), "File name is already taken")
				val allowed = document.copy(references = Some(references.copy(duplicateFilenameAllowed = Some(true))))
				assert(validator.validateFileName(allowed).get == allowed)
				val replacement = document.copy(isNextVersionOf = Some(Left(previous)))
				assert(validator.validateFileName(replacement).get == replacement)
				val automatic = document.copy(
					isNextVersionOf = Some(Left(other)),
					references = Some(references.copy(autodeprecateSameFilenameObjects = Some(true)))
				)
				assert(validator.validateFileName(automatic).get.isNextVersionOf == Some(Right(Seq(other, previous))))
			}
		}
		for (scenarios <- Seq(Seq("filename-self"), Seq("filename-unfinished"), Seq("filename-taken", "previous-deprecated"))) {
			withClue(scenarios.mkString("+")){
				withStore{ s =>
					import s.*
					scenarios.foreach(loadFixture("validation", _))
					accessGlobal{
						assert(ScopedValidator(vocab, meta).validateFileName(document).get == document)
					}
				}
			}
		}
	}

	it("validates previous versions, rejecting self replacement, unfinished predecessors, and multiple partial predecessors"){
		withStore{ s =>
			import s.*
			val validator = ScopedValidator(vocab, meta)
			loadFixture("validation", "previous-unfinished")
			val replacement = document.copy(isNextVersionOf = Some(Left(previous)))
			accessGlobal{
				assert(validator.validatePrevVers(document).isSuccess)
				for ((dto, message) <- Seq(
					document.copy(isNextVersionOf = Some(Left(hash))) -> "cannot be a next version of itself",
					replacement -> "has not been successfully uploaded",
					document.copy(isNextVersionOf = Some(Right(Seq(previous, other))),
						references = Some(references.copy(partialUpload = Some(true)))) -> "Cannot deprecate multiple"
				)) assertUserError(validator.validatePrevVers(dto), message)
			}
			loadFixture("validation", "previous-completed")
			accessGlobal(assert(validator.validatePrevVers(replacement).isSuccess))
			loadFixture("validation", "previous-deprecated")
			accessGlobal(assertUserError(validator.validatePrevVers(replacement), "already has a newer version"))
		}
	}

	it("accepts an omitted or registered licence and rejects an unknown licence"){
		withStore{ s =>
			import s.*
			val validator = ScopedValidator(vocab, meta)
			val dto = document.copy(references = Some(references.copy(licence = Some(java.net.URI("https://example.org/licence")))))
			accessGlobal{
				assert(validator.validateLicence(document).isSuccess)
				assertUserError(validator.validateLicence(dto), "Unknown licence")
			}
			loadFixture("validation", "registered-licence")
			accessGlobal(assert(validator.validateLicence(dto).isSuccess))
		}
	}

	it("requires a future moratorium and prevents adding one after publication"){
		for ((completed, existingEmbargo, requestedFuture, accepted) <- Seq(
			(false, false, true, true), (false, false, false, false),
			(true, true, true, true), (true, true, false, false), (true, false, true, false)
		)) {
			withClue(s"completed=$completed, existingEmbargo=$existingEmbargo, requestedFuture=$requestedFuture: "){
				withStore{ s =>
					import s.*
					val validator = ScopedValidator(vocab, meta)
					val future = java.time.Instant.now().plusSeconds(86400)
					if (completed) {
						loadFixture("validation", if existingEmbargo then "embargoed" else "published")
					}
					val dto = dataObject.copy(references = Some(references.copy(
						moratorium = Some(if requestedFuture then future else java.time.Instant.EPOCH)
					)))
					accessGlobal{
						assert(validator.validateMoratorium(dataObject).isSuccess)
						val result = validator.validateMoratorium(dto)
						if (accepted) assert(result.isSuccess)
						else assertUserError(result,
							if completed && !existingEmbargo then "already been published" else "must be in the future"
						)
					}
				}
			}
		}
	}
}
