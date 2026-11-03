package se.lu.nateko.cp.meta.services.upload.validation

import scala.language.unsafeNulls

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.test.services.upload.UploadTestFixture.{given, *}

class ScopedValidatorTests extends AnyFunSpec:
	it("rejects duplicate filenames unless explicitly allowed, deprecated, or automatically replaced"):
		withStore: s =>
			import s.*
			val validator = ScopedValidator(vocab, meta)
			loadFixture("validation", "filename-taken")
			server.access: conn ?=>
				given RdfLens.GlobConn = RdfLens.global(using conn)
				val rejected = validator.validateFileName(document)
				assert(rejected.failed.get.isInstanceOf[UploadUserErrorException])
				assert(rejected.failed.get.getMessage.contains("File name is already taken"))
				val allowed = document.copy(references = Some(references.copy(duplicateFilenameAllowed = Some(true))))
				assert(validator.validateFileName(allowed).get == allowed)
				val replacement = document.copy(isNextVersionOf = Some(Left(previous)))
				assert(validator.validateFileName(replacement).get == replacement)
				val automatic = document.copy(
					isNextVersionOf = Some(Left(other)),
					references = Some(references.copy(autodeprecateSameFilenameObjects = Some(true)))
				)
				assert(validator.validateFileName(automatic).get.isNextVersionOf == Some(Right(Seq(other, previous))))

	it("ignores the object's own filename, unfinished uploads, and completed deprecated objects"):
		for state <- Seq("self", "unfinished", "deprecated") do
			withClue(state):
				withStore: s =>
					import s.*
					val validator = ScopedValidator(vocab, meta)
					loadFixture("validation", s"filename-$state")
					server.access: conn ?=>
						given RdfLens.GlobConn = RdfLens.global(using conn)
						assert(validator.validateFileName(document).get == document)

	it("validates previous versions, rejecting self replacement, unfinished predecessors, and multiple partial predecessors"):
		withStore: s =>
			import s.*
			val validator = ScopedValidator(vocab, meta)
			loadFixture("validation", "previous-unfinished")
			val replacement = document.copy(isNextVersionOf = Some(Left(previous)))
			server.access: conn ?=>
				given RdfLens.GlobConn = RdfLens.global(using conn)
				assert(validator.validatePrevVers(document).isSuccess)
				for (dto, message) <- Seq(
					document.copy(isNextVersionOf = Some(Left(hash))) -> "cannot be a next version of itself",
					replacement -> "has not been successfully uploaded",
					document.copy(isNextVersionOf = Some(Right(Seq(previous, other))),
						references = Some(references.copy(partialUpload = Some(true)))) -> "Cannot deprecate multiple"
				) do
					val result = validator.validatePrevVers(dto)
					assert(result.failed.get.isInstanceOf[UploadUserErrorException])
					assert(result.failed.get.getMessage.contains(message))
			loadFixture("validation", "previous-completed")
			server.access: conn ?=>
				given RdfLens.GlobConn = RdfLens.global(using conn)
				assert(validator.validatePrevVers(replacement).isSuccess)
			loadFixture("validation", "previous-deprecated")
			server.access: conn ?=>
				given RdfLens.GlobConn = RdfLens.global(using conn)
				assert(validator.validatePrevVers(replacement).failed.get.getMessage.contains("already has a newer version"))

	it("accepts an omitted or registered licence and rejects an unknown licence"):
		withStore: s =>
			val validator = ScopedValidator(s.vocab, s.meta)
			val licence = java.net.URI("https://example.org/licence")
			val dto = document.copy(references = Some(references.copy(licence = Some(licence))))
			s.server.access: conn ?=>
				given RdfLens.GlobConn = RdfLens.global(using conn)
				assert(validator.validateLicence(document).isSuccess)
				assert(validator.validateLicence(dto).failed.get.getMessage.contains("Unknown licence"))
			s.loadFixture("validation", "registered-licence")
			s.server.access: conn ?=>
				given RdfLens.GlobConn = RdfLens.global(using conn)
				assert(validator.validateLicence(dto).isSuccess)

	it("requires a future moratorium and prevents adding one after publication"):
		for (completed, existingEmbargo, requestedFuture, accepted) <- Seq(
			(false, false, true, true), (false, false, false, false),
			(true, true, true, true), (true, true, false, false), (true, false, true, false)
		) do
			withClue(s"completed=$completed, existingEmbargo=$existingEmbargo, requestedFuture=$requestedFuture: "):
				withStore: s =>
					import s.*
					val validator = ScopedValidator(vocab, meta)
					val future = java.time.Instant.now().plusSeconds(86400)
					if completed then
						loadFixture("validation", if existingEmbargo then "embargoed" else "published")
					val dto = dataObject.copy(references = Some(references.copy(
						moratorium = Some(if requestedFuture then future else java.time.Instant.EPOCH)
					)))
					server.access: conn ?=>
						given RdfLens.GlobConn = RdfLens.global(using conn)
						assert(validator.validateMoratorium(dataObject).isSuccess)
						val result = validator.validateMoratorium(dto)
						assert(result.isSuccess == accepted)
						if !accepted then
							assert(result.failed.get.isInstanceOf[UploadUserErrorException])
							assert(result.failed.get.getMessage.contains(
								if completed && !existingEmbargo then "already been published" else "must be in the future"
							))
