package se.lu.nateko.cp.meta.utils

import scala.util.Try
import scala.util.Success
import scala.util.Failure

object HashSumUtils {

	private[this] val urlEncoder = java.util.Base64.getUrlEncoder.withoutPadding
	private[this] val sha256Pattern = """[0-9a-fA-F]{64}""".r.pattern

	def toUrlSafeSha256Base64(hex: String): Try[String] = ensureSha256(hex)
		.map(javax.xml.bind.DatatypeConverter.parseHexBinary)
		.map(urlEncoder.encodeToString)

	def ensureSha256(sum: String): Try[String] = {
		if(sha256Pattern.matcher(sum).matches) Success(sum.toLowerCase)
		else Failure(new IllegalArgumentException("Invalid SHA-256 sum, expecting a 32-byte hexadecimal string"))
	}

}