package se.lu.nateko.cp.meta.test.services.citation

import scala.language.unsafeNulls

import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.core.data.{Person, UriResource}
import se.lu.nateko.cp.meta.services.citation.AttributionProvider.*

import java.net.URI
import java.time.Instant

class AttributionProviderTests extends AnyFunSuite:

	private val piRole = UriResource(URI.create("http://meta.icos-cp.eu/resources/roles/PI"), None, Nil)

	private def membership(name: String, weight: Option[Int], year: Option[Int]): Membership =
		val person = Person(UriResource(URI.create(s"https://example.org/$name"), None, Nil), "Test", name, None, None)
		val start = year.map(y => Instant.parse(s"$y-01-01T00:00:00Z"))
		Membership(person, RoleDetails(piRole, start, None, weight, None))

	test("equal order puts the newer PI first, in ETC ordering"):
		val previous = membership("Cremonese", Some(-1), Some(2018))
		val current = membership("Galvagno", Some(-1), Some(2023))
		assert(Seq(previous, current).sorted(using ecoMembershipOrdering) == Seq(current, previous))

	test("explicit order takes precedence over recency, including ties below order one"):
		val first = membership("First", Some(-1), Some(2010))
		val olderSecond = membership("Alpha", Some(-2), Some(2020))
		val newerSecond = membership("Zulu", Some(-2), Some(2024))
		assert(Seq(olderSecond, newerSecond, first).sorted(using ecoMembershipOrdering) == Seq(first, newerSecond, olderSecond))

	test("unranked authors follow ranked authors and remain alphabetical regardless of dates"):
		val ranked = membership("Ranked", Some(-3), Some(2010))
		val alpha = membership("Alpha", None, Some(2015))
		val zulu = membership("Zulu", None, Some(2024))
		assert(Seq(zulu, alpha, ranked).sorted(using ecoMembershipOrdering) == Seq(ranked, alpha, zulu))

	test("equal explicit orders with equal start dates fall back to alphabetical ordering"):
		val alpha = membership("Alpha", Some(-1), Some(2023))
		val zulu = membership("Zulu", Some(-1), Some(2023))
		assert(Seq(zulu, alpha).sorted(using ecoMembershipOrdering) == Seq(alpha, zulu))

	test("missing start dates follow known dates for equal explicit orders"):
		val unknownAlpha = membership("Alpha", Some(-1), None)
		val unknownBeta = membership("Beta", Some(-1), None)
		val known = membership("Zulu", Some(-1), Some(2023))
		assert(Seq(unknownBeta, unknownAlpha, known).sorted(using ecoMembershipOrdering) == Seq(known, unknownAlpha, unknownBeta))

	test("sorting before deduplication uses each person's highest priority membership"):
		val previousPi = membership("Cremonese", Some(-1), Some(2018))
		val currentPi = membership("Galvagno", Some(-1), Some(2023))
		val currentEarlierRole = membership("Galvagno", Some(-3), Some(2018))
		val authors = Seq(previousPi, currentEarlierRole, currentPi).sorted(using ecoMembershipOrdering).map(_.person).distinct
		assert(authors == Seq(currentPi.person, previousPi.person))

	test("default ordering keeps equal weights alphabetical regardless of dates"):
		val older = membership("Zulu", Some(70), Some(2010))
		val newer = membership("Alpha", Some(70), Some(2024))
		val newest = membership("Mike", Some(70), Some(2025))
		assert(Seq(newest, older, newer).sorted == Seq(newer, newest, older))

	test("default ordering still puts higher weights first"):
		val pi = membership("Zulu", Some(100), Some(2010))
		val deputy = membership("Alpha", Some(20), Some(2024))
		assert(Seq(deputy, pi).sorted == Seq(pi, deputy))
