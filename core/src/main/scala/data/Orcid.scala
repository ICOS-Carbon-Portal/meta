package se.lu.nateko.cp.meta.core.data

class Orcid private(val shortId: String) {

	def id = s"https://orcid.org/$shortId"

	override def equals(other: Any): Boolean = other match{
		case orcid: Orcid => shortId.equals(orcid.shortId)
		case _ => false
	}

	override def hashCode(): Int = shortId.hashCode()

	override def toString(): String = id
}

object Orcid{

	def unapply(s: String): Option[Orcid] = {

		val digits = s.iterator.filter(isDigit).take(15).toIndexedSeq

		if(digits.size == 15) {
			val checksumChar = s.last.toUpper

			if(checksumCorrect(digits, checksumChar)){
				val shortId = digits.iterator.sliding(4, 4).map(_.mkString).mkString("", "-", checksumChar.toString)
				Some(new Orcid(shortId))
			} else None

		} else None
	}

	private def checksumCorrect(digits: IndexedSeq[Char], checksumChar: Char): Boolean = {
		var total: Int = 0
		var i = 0
		while(i < 15){
			total = (total + Character.getNumericValue(digits(i))) * 2
			i += 1
		}
		val checksumExpected = (12 - (total % 11)) % 11

		val checksumDeclared = if(checksumChar == 'X') 10
			else if(isDigit(checksumChar)) Character.getNumericValue(checksumChar)
			else Int.MinValue

		checksumDeclared == checksumExpected
	}

	private def isDigit(c: Char): Boolean = c >= '0' && c <= '9'

}
