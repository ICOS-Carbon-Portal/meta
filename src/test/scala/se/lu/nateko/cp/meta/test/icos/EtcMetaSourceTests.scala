package se.lu.nateko.cp.meta.test.icos

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.icos.InstrumentDeployment
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.api.UriId
import java.time.Instant
import se.lu.nateko.cp.meta.core.data.PositionUtil

class EtcMetaSourceTests extends AnyFunSpec{

	import se.lu.nateko.cp.meta.icos.EtcMetaSource.*

	private def mkDepl(stId: Int, site: String, pos: Position, varName: String, start: String, stop: Option[String] = None, cpId: Option[String] = None) =
		InstrumentDeployment[E](
			UriId(cpId.getOrElse("")),
			makeId(stId.toString),
			UriId(s"ES_$site"),
			Some(pos),
			Some(varName),
			Some(Instant.parse(start)),
			stop.map(Instant.parse)
		)

	val p1 = Position(0, 0, Some(-1.5f), None)
	val p2 = Position(10, 20, Some(-1.5f), None)
	val p3 = Position(50, 50, Some(-0.5f), None)

	describe("mergeInstrDeployments"){
		val input = Seq(
			"sens1" -> mkDepl(22, "SE-Htm", p1, "VAR1", "2020-05-02T10:00:00Z"),
			"sens1" -> mkDepl(22, "SE-Htm", p2, "VAR1", "2020-06-02T10:00:00Z"),
			"sens2" -> mkDepl(22, "SE-Htm", p2, "VAR1", "2020-07-02T10:00:00Z"),
			"sens1" -> mkDepl( 2, "SE-Deg", p3, "VARx", "2020-06-15T10:00:00Z"),
			"sens3" -> mkDepl( 2, "SE-Deg", p3, "VARx", "2019-05-01T10:00:00Z"),
		)

		val input2 = Seq(
			"sens1" -> mkDepl(22, "SE-Htm", p1, "VAR1", "2020-01-01T10:00:00Z"),
			"sens1" -> mkDepl(22, "SE-Htm", p2, "VAR1", "2020-02-01T10:00:00Z"),
			"sens1" -> mkDepl(22, "SE-Htm", p3, "VAR1", "2020-03-01T10:00:00Z"),
			"sens1" -> mkDepl(22, "SE-Htm", p3, "VAR1", "2020-05-01T10:00:00Z"),
			"sens2" -> mkDepl(22, "SE-Htm", p1, "VAR1", "2020-04-01T10:00:00Z"),
		)

		it("works on empty input"){
			assert(mergeInstrDeployments(Seq.empty).isEmpty)
		}

		it("works on single-elem input, inserts cpId"){
			val single = input.take(1)
			val (sensId, depl) = single.head
			assert(mergeInstrDeployments(single) === Map(sensId -> Seq(depl.copy(cpId = UriId(s"${sensId}_0")))))
		}

		it("merges the sequence of instrument deployments as expected"){
			val output = mergeInstrDeployments(input)
			val p1_2 = PositionUtil.average(Iterable(p1, p2)).get

			assert(output === Map(
				"sens1" -> Seq(
					mkDepl(22, "SE-Htm", p1_2, "VAR1", "2020-05-02T10:00:00Z", Some("2020-06-15T10:00:00Z"), Some("sens1_0")),
					mkDepl( 2, "SE-Deg", p3, "VARx", "2020-06-15T10:00:00Z", None, Some("sens1_1"))
				),

				"sens2" -> Seq(
					mkDepl(22, "SE-Htm", p2, "VAR1", "2020-07-02T10:00:00Z", None, Some("sens2_0"))
				),

				"sens3" -> Seq(
					mkDepl( 2, "SE-Deg", p3, "VARx", "2019-05-01T10:00:00Z", Some("2020-06-15T10:00:00Z"), Some("sens3_0"))
				)

			))
		}

		it("works with temporally unsorted records"){
			val out = mergeInstrDeployments(input2)
			assert(out("sens1").size === 2)
			assert(out("sens2").size === 1)
		}
	}

}
