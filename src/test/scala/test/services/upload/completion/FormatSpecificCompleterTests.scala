package se.lu.nateko.cp.meta.services.upload.completion

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.HandleNetClientConfig
import se.lu.nateko.cp.meta.api.{HandleNetClient, RdfLens}
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.StatementSource.getStatements
import se.lu.nateko.cp.meta.test.services.upload.UploadTestFixture.{given, *}

import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class FormatSpecificCompleterTests extends AnyFunSpec with BeforeAndAfterAll:
	private given ActorSystem = ActorSystem("format-specific-completer-tests")
	private given Materializer = Materializer(summon[ActorSystem])
	private val handles = new HandleNetClient(HandleNetClientConfig(
		Map(Envri.ICOS -> "test"), "https://example.org", None, "", "", dryRun = true
	))
	private val interval = TimeInterval(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2020-01-02T00:00:00Z"))

	override def afterAll(): Unit =
		try Await.result(summon[ActorSystem].terminate(), 10.seconds)
		finally super.afterAll()

	it("replaces extracted columns and acquisition times, updates rows only when supplied, and is idempotent"):
		for rows <- Seq(None, Some(42)) do
			withClue(s"rows=$rows: "):
				withStore: s =>
					import s.*
					loadFixture("completion", "time-series-before")
					val lens: RdfLens.DobjLens = RdfLens.dobjLens(java.net.URI(server.writeContext.stringValue), Seq(java.net.URI(server.writeContext.stringValue)))
					val extract = TimeSeriesExtract(TabularIngestionExtract(Some(Seq("CO2", "time")), interval), rows)
					val completer = new TimeSeriesUploadCompleter(extract, lens, handles, vocab, meta)
					val updates = server.access(completer.getUpdates(hash))
					assert(updates.count(!_.isAssertion) == (if rows.isDefined then 4 else 3))
					server.applyAll(updates)().get
					val expected = fixture("completion", if rows.isDefined then "time-series-with-rows" else "time-series-without-rows")
					val actual = server.access(getStatements(null, null, null).toSet)
					assert(actual == expected.map(st => factory.createStatement(st.getSubject, st.getPredicate, st.getObject, server.writeContext)))
					assert(server.access(completer.getUpdates(hash)).isEmpty)

	it("replaces NetCDF variable bounds while preserving metadata for other variables"):
		withStore: s =>
			import s.*
			loadFixture("completion", "netcdf-before")
			val lens: RdfLens.DobjLens = RdfLens.dobjLens(java.net.URI(server.writeContext.stringValue), Seq(java.net.URI(server.writeContext.stringValue)))
			val completer = new NetCdfUploadCompleter(NetCdfExtract(Seq(VarInfo("CO2", 350, 450))), handles, lens, vocab, meta)
			val updates = server.access(completer.getUpdates(hash))
			assert(updates.count(!_.isAssertion) == 2)
			assert(updates.count(_.isAssertion) == 2)
			server.applyAll(updates)().get
			val expected = fixture("completion", "netcdf-after")
			assert(server.access(getStatements(null, null, null).toSet) == expected.map(st => factory.createStatement(st.getSubject, st.getPredicate, st.getObject, server.writeContext)))
			assert(server.access(completer.getUpdates(hash)).isEmpty)
