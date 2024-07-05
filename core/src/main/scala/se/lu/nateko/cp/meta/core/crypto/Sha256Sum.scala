package se.lu.nateko.cp.meta.core.crypto

import java.util.Arrays
import java.util.Base64

import scala.util.{Try, Success, Failure}
import scala.util.control.NoStackTrace


class Sha256Sum(private val bytes: Array[Byte]) extends java.io.Serializable derives CanEqual{
	import Sha256Sum.*
	assert(byteLengthCorrect(bytes), byteLengthMessage)

	def getBytes: Seq[Byte] = bytes.toSeq
	def isTruncated: Boolean = bytes.length < 32
	def truncate: Sha256Sum = if(isTruncated) this else new Sha256Sum(bytes.take(18))

	def base64: String = Base64.getEncoder.withoutPadding.encodeToString(bytes)
	def base64Url: String = Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
	def hex: String = bytes.iterator.map(Sha256Sum.formatByte).mkString

	/**
	 * URL- and filename-friendly id that is sufficiently unique.
	 * Contains 18 bytes of binary information, base64Url-encoded in 24 symbols.
	 * Even after upper-casing, truncating to 24 symbols encodes 15.74 bytes of information.
	 * This is almost as much as UUIDs, which have 16 bytes.
	 * The amount of combinations (38^24 = 8.22e37) is only 4.14 times less than for a random UUID.
	 * The number of combinations of a single symbol after upper-casing is 38 = 64 - 26 .
	 */
	def id: String = base64Url.substring(0, Sha256Sum.IdLength)

	override def equals(other: Any): Boolean =
		if(other.isInstanceOf[Sha256Sum]){
			val hash2 = other.asInstanceOf[Sha256Sum]
			if(this.isTruncated == hash2.isTruncated)
				Arrays.equals(this.bytes, hash2.bytes)
			else
				Arrays.equals(this.truncate.bytes, hash2.truncate.bytes)
		}
		else false

	override def hashCode: Int = Arrays.hashCode(bytes.take(18))

	override def toString: String = base64
}

object Sha256Sum {

	val IdLength = 24

	def fromBase64(hash: String): Try[Sha256Sum] = Try(Base64.getDecoder.decode(hash)).flatMap(fromBytes)
	def fromBase64Url(hash: String): Try[Sha256Sum] = Try(Base64.getUrlDecoder.decode(hash)).flatMap(fromBytes)
	def fromHex(hash: String): Try[Sha256Sum] = Try(parseHexArray(hash)).flatMap(fromBytes)

	def fromString(hash: String): Try[Sha256Sum] = fromHex(hash).orElse(
		fromBase64Url(hash).orElse(
			fromBase64(hash).orElse(Failure(new Exception(
				"Could not parse SHA-256 hashsum, expected a 32- or 18-byte array, either hex-, Base64Url-, or Base64-encoded"
			)))
		)
	)

	def fromBytes(bytes: Array[Byte]): Try[Sha256Sum] =
		if(byteLengthCorrect(bytes))
			Success(new Sha256Sum(bytes))
		else
			Failure(new IllegalArgumentException(byteLengthMessage) with NoStackTrace)


	def unapply(hash: String): Option[Sha256Sum] = fromString(hash).toOption

	val formatByte: Byte => String = b => String.format("%02x", Int.box(255 & b))

	def parseHexArray(hex: String): Array[Byte] = {
		val strLen = hex.length
		assert(strLen % 2 == 0, "hex string must have even number of characters")
		Array.tabulate(strLen / 2){i =>
			Integer.parseInt(hex.substring(i * 2, (i + 1) * 2), 16).toByte
		}
	}

	def byteLengthCorrect(bytes: Array[Byte]): Boolean = bytes.length == 32 || bytes.length == 18
	val byteLengthMessage = "SHA-256 hash sum must be 32 (complete) or 18 (truncated) bytes long"
}
