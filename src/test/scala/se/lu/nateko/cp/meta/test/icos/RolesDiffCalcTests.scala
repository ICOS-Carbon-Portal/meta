package se.lu.nateko.cp.meta.test.icos

import java.time.Instant
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.icos.Membership
import se.lu.nateko.cp.meta.icos.RolesDiffCalc

class RolesDiffCalcTests extends AnyFunSpec{

	describe("resultingMembsForSameAssumedRole"){

		it("produces expected memberships for the scenario of two-interval intermittent role end"){
			val current = Seq(
				Membership("a1", null, None, instant("2015")),
				Membership("a2", null, instant("2016"), None)
			)
			val latest = Seq(
				Membership("", null, None, instant("2015")),
				Membership("", null, instant("2016"), instant("2017"))
			)
			val expected = Seq(
				Membership("a1", null, None, instant("2015")),
				Membership("a2", null, instant("2016"), instant("2017"))
			)
			assert(RolesDiffCalc.resultingMembsForSameAssumedRole(current, latest) == expected)
		}
	}

	private def instant(date: String): Option[Instant] = Some(Instant.parse(s"${date}-01-01T12:00:00Z"))
}
