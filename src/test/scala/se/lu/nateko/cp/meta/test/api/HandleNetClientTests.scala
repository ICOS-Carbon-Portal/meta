package se.lu.nateko.cp.meta.test.api

import scala.language.unsafeNulls

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum.formatByte

import java.nio.file.{Files, Paths}

class HandleNetClientTests extends AnyFunSpec{

	describe("getHandleNetKeyBytes"){

		def resourcePathToPath(path: String) = Paths.get(getClass.getResource(path).toURI)

		it("correctly converts PKCS8 public key to the bytes array expected by Handle.net software"){
			val expectedPath = resourcePathToPath("/crypto/handleNetPubKey.bin")
			val expectedBytes = Files.readAllBytes(expectedPath)

			val key = HandleNetClient.readPublicKey(resourcePathToPath("/crypto/pkcs8PubKey.der"))
			val bytes = HandleNetClient.getHandleNetKeyBytes(key)
			assert(bytes.size === expectedBytes.size)
			assert(bytes.map(formatByte) === expectedBytes.map(formatByte))
		}
	}
}
