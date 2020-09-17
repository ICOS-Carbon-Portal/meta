package se.lu.nateko.cp.meta.test.icos

import java.time.Instant
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.icos.Membership
import se.lu.nateko.cp.meta.icos.RolesDiffCalc

class RolesDiffCalcTests extends AnyFunSpec{

	describe("resultingMembsForSameAssumedRole"){

		it("produces expected memberships for the scenario of two-interval intermittent role end"){
			val current = Seq(
				Membership(UriId("a1"), null, None, instant("2015")),
				Membership(UriId("a2"), null, instant("2016"), None)
			)
			val latest = Seq(
				Membership(UriId(""), null, None, instant("2015")),
				Membership(UriId(""), null, instant("2016"), instant("2017"))
			)
			val expected = Seq(
				Membership(UriId("a1"), null, None, instant("2015")),
				Membership(UriId("a2"), null, instant("2016"), instant("2017"))
			)
			assert(RolesDiffCalc.resultingMembsForSameAssumedRole(current, latest).sortBy(_.cpId.toString) == expected)
		}
	}

	private def instant(date: String): Option[Instant] = Some(Instant.parse(s"${date}-01-01T12:00:00Z"))
}
