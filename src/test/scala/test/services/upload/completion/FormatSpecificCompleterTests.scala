package se.lu.nateko.cp.meta.services.upload.completion

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.HandleNetClientConfig
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.instanceserver.StatementSource.getStatements
import se.lu.nateko.cp.meta.test.services.upload.UploadTestFixture.{given, *}

import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class FormatSpecificCompleterTests extends AnyFunSpec with BeforeAndAfterAll{
	private given ActorSystem = ActorSystem("format-specific-completer-tests")
	private given Materializer = Materializer(summon[ActorSystem])
	private val handles = new HandleNetClient(HandleNetClientConfig(
		Map(Envri.ICOS -> "test"), "https://example.org", None, "", "", dryRun = true
	))
	private val interval = TimeInterval(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2020-01-02T00:00:00Z"))

	override def afterAll(): Unit =
		try Await.result(summon[ActorSystem].terminate(), 10.seconds)
		finally super.afterAll()

	/** Applies the completer's updates, compares the whole store with the scenario, and checks idempotency. */
	private def completeAndVerify(s: Store, completer: FormatSpecificCompleter, expectedScenario: String): Seq[RdfUpdate] = {
		import s.*
		val updates = server.access(completer.getUpdates(hash))
		server.applyAll(updates)().get
		val expected = fixture("completion", expectedScenario)
			.map(st => factory.createStatement(st.getSubject, st.getPredicate, st.getObject, server.writeContext))
		assert(server.access(getStatements(null, null, null).toSet) == expected)
		assert(server.access(completer.getUpdates(hash)).isEmpty)
		updates
	}

	it("replaces extracted columns and acquisition times, updates rows only when supplied, and is idempotent"){
		for (rows <- Seq(None, Some(42))) {
			withClue(s"rows=$rows: "){
				withStore{ s =>
					s.loadFixture("completion", "time-series-before")
					val extract = TimeSeriesExtract(TabularIngestionExtract(Some(Seq("CO2", "time")), interval), rows)
					val completer = new TimeSeriesUploadCompleter(extract, s.dobjLens, handles, s.vocab, s.meta)
					val expected = if rows.isDefined then "time-series-with-rows" else "time-series-without-rows"
					val updates = completeAndVerify(s, completer, expected)
					assert(updates.count(!_.isAssertion) == (if rows.isDefined then 4 else 3))
				}
			}
		}
	}

	it("replaces NetCDF variable bounds while preserving metadata for other variables"){
		withStore{ s =>
			s.loadFixture("completion", "netcdf-before")
			val completer = new NetCdfUploadCompleter(NetCdfExtract(Seq(VarInfo("CO2", 350, 450))), handles, s.dobjLens, s.vocab, s.meta)
			val updates = completeAndVerify(s, completer, "netcdf-after")
			assert(updates.count(!_.isAssertion) == 2)
			assert(updates.count(_.isAssertion) == 2)
		}
	}
}
