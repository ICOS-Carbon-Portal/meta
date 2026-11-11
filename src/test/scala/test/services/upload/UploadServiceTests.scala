package se.lu.nateko.cp.meta.test.services.upload

import scala.language.unsafeNulls

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.services.upload.UploadService
import UploadTestFixture.{hash, other}

import java.net.URI

class UploadServiceTests extends AnyFunSpec{
	it("computes collection identity from member hashes only, rejecting non-static member URLs"){
		val first = URI(s"https://example.org/objects/${hash.id}")
		val second = URI(s"https://example.org/collections/${other.id}")
		val expected = UploadService.collectionHash(Seq(first, second)).get
		assert(UploadService.collectionHash(Seq(second, first)).get == expected)
		assert(UploadService.collectionHash(Seq(URI(s"https://another.org/objects/${hash.base64Url}"), second)).get == expected)
		assert(UploadService.collectionHash(Seq(first)).get != expected)
		assert(UploadService.collectionHash(Seq(first, first)).get != expected)
		for (member <- Seq("https://example.org/stations/station", "https://example.org/objects/invalid")) {
			val result = UploadService.collectionHash(Seq(URI(member)))
			assert(result.failed.get.isInstanceOf[UploadUserErrorException])
			assert(result.failed.get.getMessage.contains("must end with base64Url-encoded SHA-256"))
		}
	}
}
