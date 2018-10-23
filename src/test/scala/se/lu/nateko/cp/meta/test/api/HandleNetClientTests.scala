package se.lu.nateko.cp.meta.test.api

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.api.HandleNetClient
import java.nio.file.Paths
import java.nio.file.Files
import javax.xml.bind.DatatypeConverter

class HandleNetClientTests extends FunSpec{

	describe("getHandleNetKeyBytes"){

		def resourcePathToPath(path: String) = Paths.get(getClass.getResource(path).toURI)

		it("correctly converts PKCS8 public key to the bytes array expected by Handle.net software"){
			val expectedPath = resourcePathToPath("/crypto/handleNetPubKey.bin")
			val expectedBytes = Files.readAllBytes(expectedPath)

			val key = HandleNetClient.readPublicKey(resourcePathToPath("/crypto/pkcs8PubKey.der"))
			val bytes = HandleNetClient.getHandleNetKeyBytes(key)
			assert(bytes.size === expectedBytes.size)
			assert(DatatypeConverter.printHexBinary(bytes) === DatatypeConverter.printHexBinary(expectedBytes))
		}
	}
}
