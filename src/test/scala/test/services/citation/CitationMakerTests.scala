package se.lu.nateko.cp.meta.test.services.citation

import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.core.data.{Person, UriResource}
import se.lu.nateko.cp.meta.services.citation.CitationMaker

import java.net.URI

class CitationMakerTests extends AnyFunSuite:

	private def format(firstName: String, lastName: String = "Schmid"): String =
		val person = Person(UriResource(new URI("https://example.org/person"), None, Nil), firstName, lastName, None, None)
		CitationMaker.formatPersonName(person)

	test("uses all given-name initials"):
		assert(format("Hans Peter") == "Schmid, H.P.")

	test("single given names retain their existing format"):
		assert(format("Ingo", "Völksch") == "Völksch, I.")

	test("ignores leading, trailing and repeated whitespace including nonbreaking spaces"):
		assert(format("  Hans\t Peter\n ") == "Schmid, H.P.")
		assert(format("Hans\u00a0Peter") == "Schmid, H.P.")

	test("preserves hyphens between initials"):
		assert(format("Jean-Pierre", "Dupont") == "Dupont, J.-P.")
		assert(format("Hans Peter-Paul") == "Schmid, H.P.-P.")

	test("empty given names remain invalid so broken objects do not produce biblioinfo"):
		intercept[IllegalArgumentException]:
			format("")

	test("whitespace-only given names are invalid, including Unicode whitespace"):
		Seq(" ", " \t\n ", "\u00a0", "\u2003").foreach: name =>
			intercept[IllegalArgumentException]:
				format(name)

	test("ignores empty hyphenated components and preserves accented initials"):
		assert(format("-Hans--Peter-") == "Schmid, H.-P.")
		assert(format("---") == "Schmid")
		assert(format("Émile André") == "Schmid, É.A.")
