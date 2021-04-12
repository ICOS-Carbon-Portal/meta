package se.lu.nateko.cp.meta.core.data

import java.util.regex.Pattern
import java.util.Locale

/***
 * ISO ALPHA-2 country code
 */
class CountryCode private (val code: String, val displayCountry: String){

	override def equals(other: Any) = other match{
		case otherCc: CountryCode => otherCc.code == code
		case _ => false
	}

	override def hashCode: Int = code.hashCode

	override def toString = code

}

object CountryCode{

	private val pattern = Pattern.compile("[A-Z]{2}")

	private def normalize(cc: String): String = cc.replace("UK", "GB")

	def unapply(s: String): Option[CountryCode] =
		if(pattern.matcher(s).matches) {
			val code = normalize(s)
			val country = new Locale("", code).getDisplayCountry
			if(code != country) Some(new CountryCode(code, country))
			else None
		} else None

	def unapply(id: CountryCode): Option[String] = Some(id.code)
}
