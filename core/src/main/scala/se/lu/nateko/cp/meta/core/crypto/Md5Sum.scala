package se.lu.nateko.cp.meta.core.crypto

import java.util.Arrays

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import javax.xml.bind.DatatypeConverter

class Md5Sum(private val bytes: Array[Byte]) {

	assert(bytes.length == 16, "MD5 hash sum must be 16 bytes long")

	def getBytes: Seq[Byte] = bytes

	def hex: String = DatatypeConverter.printHexBinary(bytes).toLowerCase

	override def equals(other: Any): Boolean =
		if(other.isInstanceOf[Md5Sum])
			Arrays.equals(bytes, other.asInstanceOf[Md5Sum].bytes)
		else false

	override def hashCode: Int = Arrays.hashCode(bytes)

	override def toString: String = hex
}

object Md5Sum {

	def fromHex(hash: String): Try[Md5Sum] = Try{
		new Md5Sum(DatatypeConverter.parseHexBinary(hash))
	}

	def fromString(hash: String): Try[Md5Sum] = fromHex(hash).orElse(Failure(
		new Exception("Could not parse MD5 hashsum, expected a hex-encoded 16-byte array")
	))

}
