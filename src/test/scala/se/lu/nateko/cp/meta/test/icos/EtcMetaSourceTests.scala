package se.lu.nateko.cp.meta.test.icos

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.metaflow.InstrumentDeployment
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.api.UriId
import java.time.Instant
import se.lu.nateko.cp.meta.core.data.PositionUtil

class EtcMetaSourceTests extends AnyFunSpec{

	import se.lu.nateko.cp.meta.metaflow.icos.EtcMetaSource.*

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

	val p1 = Position(0, 0, Some(-1.5f), None, None)
	val p2 = Position(10, 20, Some(-1.5f), None, None)
	val p3 = Position(50, 50, Some(-0.5f), None, None)

	describe("mergeInstrDeployments"){
		val input = Seq(
			"sens1" -> mkDepl(22, "SE-Htm", p1, "SWC_1_1_1", "2020-05-02T10:00:00Z"),
			"sens1" -> mkDepl(22, "SE-Htm", p2, "SWC_1_1_1", "2020-06-02T10:00:00Z"),
			"sens2" -> mkDepl(22, "SE-Htm", p2, "SWC_1_1_1", "2020-07-02T10:00:00Z"),
			"sens1" -> mkDepl( 2, "SE-Deg", p3, "SWC_2_1_1", "2020-06-15T10:00:00Z"),
			"sens3" -> mkDepl( 2, "SE-Deg", p3, "SWC_2_1_1", "2019-05-01T10:00:00Z"),
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
					mkDepl(22, "SE-Htm", p1_2, "SWC_1_1_1", "2020-05-02T10:00:00Z", Some("2020-06-15T10:00:00Z"), Some("sens1_0")),
					mkDepl( 2, "SE-Deg", p3, "SWC_2_1_1", "2020-06-15T10:00:00Z", None, Some("sens1_1"))
				),

				"sens2" -> Seq(
					mkDepl(22, "SE-Htm", p2, "SWC_1_1_1", "2020-07-02T10:00:00Z", None, Some("sens2_0"))
				),

				"sens3" -> Seq(
					mkDepl( 2, "SE-Deg", p3, "SWC_2_1_1", "2019-05-01T10:00:00Z", Some("2020-06-15T10:00:00Z"), Some("sens3_0"))
				)

			))
		}

		it("works with temporally unsorted records"){
			val input = Seq(
				"sens1" -> mkDepl(22, "SE-Htm", p1, "VAR1", "2020-01-01T10:00:00Z"),
				"sens1" -> mkDepl(22, "SE-Htm", p2, "VAR1", "2020-02-01T10:00:00Z"),
				"sens1" -> mkDepl(22, "SE-Htm", p3, "VAR1", "2020-03-01T10:00:00Z"),
				"sens1" -> mkDepl(22, "SE-Htm", p3, "VAR1", "2020-05-01T10:00:00Z"),
				"sens2" -> mkDepl(22, "SE-Htm", p1, "VAR1", "2020-04-01T10:00:00Z"),
			)
			val out = mergeInstrDeployments(input)
			assert(out("sens1").size === 2)
			assert(out("sens2").size === 1)
		}

		it("allows multi-variable-measuring sensors"):
			val input = Seq(
				"sens1" -> mkDepl(22, "SE-Htm", p1, "VAR1", "2020-01-01T10:00:00Z"),
				"sens1" -> mkDepl(22, "SE-Htm", p1, "VAR2", "2020-01-01T10:00:00Z"),
			)
			val output = mergeInstrDeployments(input)

			assert(output === Map(
				"sens1" -> Seq(
					mkDepl(22, "SE-Htm", p1, "VAR1", "2020-01-01T10:00:00Z", None, Some("sens1_0")),
					mkDepl(22, "SE-Htm", p1, "VAR2", "2020-01-01T10:00:00Z", None, Some("sens1_1")),
				)
			))

		it("allows multi-var sensor to have unequal amount of deployments to the different types of vars"):
			val p1 = Position(46.58659817, 11.4341221, Some(-0.05f), None, None)
			val p2 = Position(46.58659821, 11.43412203, Some(-0.05f), None, None)
			val input = Seq(
				"70dcf602" -> mkDepl(15, "IT-Ren", p1, "SWC_4_1_1", "2020-01-01T11:00:00Z"),
				"70dcf602" -> mkDepl(15, "IT-Ren", p1, "TS_4_2_1", "2020-01-01T11:00:00Z"),
				"70dcf602" -> mkDepl(15, "IT-Ren", p2, "SWC_4_1_1", "2020-12-07T11:00:00Z"),
				"70dcf602" -> mkDepl(15, "IT-Ren", p2, "TS_4_2_2", "2020-12-07T11:00:00Z")
			)
			val output = mergeInstrDeployments(input)
			val pA = PositionUtil.average(Seq(p1, p2)).get
			assert(output === Map(
				"70dcf602" -> Seq(
					mkDepl(15, "IT-Ren", pA, "SWC_4_1_1", "2020-01-01T11:00:00Z", None, Some("70dcf602_0")),
					mkDepl(15, "IT-Ren", p1, "TS_4_2_1", "2020-01-01T11:00:00Z", Some("2020-12-07T11:00:00Z"), Some("70dcf602_1")),
					mkDepl(15, "IT-Ren", p2, "TS_4_2_2", "2020-12-07T11:00:00Z", None, Some("70dcf602_2"))
				)
			))
	}

}
