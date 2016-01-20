package se.lu.nateko.cp.meta.core.crypto

import java.util.Base64
import scala.util.Try
import java.util.Arrays
import javax.xml.bind.DatatypeConverter
import spray.json._
import scala.util.Success
import scala.util.Failure

class Sha256Sum(private val bytes: Array[Byte]) {

	assert(bytes.length == 32, "SHA-256 hash sum must be 32 bytes long")

	def getBytes: Seq[Byte] = bytes

	def base64: String = Base64.getEncoder.encodeToString(bytes)
	def base64Url: String = Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
	def hex: String = DatatypeConverter.printHexBinary(bytes)

	override def equals(other: Any): Boolean =
		if(other.isInstanceOf[Sha256Sum])
			Arrays.equals(bytes, other.asInstanceOf[Sha256Sum].bytes)
		else false

	override def hashCode: Int = Arrays.hashCode(bytes)

	override def toString: String = base64Url
}

object Sha256Sum extends DefaultJsonProtocol{

	def fromBase64(hash: String): Try[Sha256Sum] = Try{
		new Sha256Sum(Base64.getDecoder.decode(hash))
	}

	def fromBase64Url(hash: String): Try[Sha256Sum] = Try{
		new Sha256Sum(Base64.getUrlDecoder.decode(hash))
	}

	def fromHex(hash: String): Try[Sha256Sum] = Try{
		new Sha256Sum(DatatypeConverter.parseHexBinary(hash))
	}

	private def tryAll(hash: String): Try[Sha256Sum] =
		fromHex(hash).orElse(fromBase64Url(hash)).orElse(fromBase64(hash))

		implicit object sha256sumFormat extends RootJsonFormat[Sha256Sum]{

		def write(hash: Sha256Sum) = JsString(hash.base64Url)

		def read(value: JsValue): Sha256Sum = value match{
			case JsString(s) => tryAll(s) match {
				case Success(hash) => hash
				case Failure(err) => deserializationError(
					"Could not parse SHA-256 hashsum, expected a 32-byte array, either hex- or Base64-encoded",
					err
				)
			}
			case _ => deserializationError("Expected a Base64Url-encoded 32-byte string")
		}
	}

}
