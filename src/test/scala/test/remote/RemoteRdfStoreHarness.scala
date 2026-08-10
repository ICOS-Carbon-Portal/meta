package se.lu.nateko.cp.meta.test.remote

import scala.language.unsafeNulls

import java.io.File
import java.net.{InetSocketAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.concurrent.duration.*
import scala.util.{Try, Using}

/**
 * Boots a real `rdfStore` process (forked JVM, not an in-process simulation) against a
 * temporary LMDB directory, plus a throwaway PostgreSQL instance for the RDF-update logs that
 * rdfStore reads during fresh-store initialization.
 *
 * This is the harness for task 19 in docs/rdf-common-split ("Add a remote integration test on
 * LMDB") - the only test in the plan that actually exercises `meta -> HTTP -> rdfStore -> LMDB`
 * end to end, instead of compile-time wiring.
 *
 * Requires `initdb` and `pg_ctl` on PATH (see PostgresFixture).
 */
final class RemoteRdfStoreHarness private (
	private val pg: RemoteRdfStoreHarness.PostgresFixture,
	private val storeProcess: RemoteRdfStoreHarness.StoreProcess
):
	import RemoteRdfStoreHarness.*

	val storePort: Int = storeProcess.port
	val baseUri: String = s"http://127.0.0.1:$storePort"
	val queryEndpoint: String = s"$baseUri/internal/sparql"
	val updateEndpoint: String = s"$baseUri/internal/sparql"
	val adminReadOnlyEndpoint: String = s"$baseUri/admin/read-only"

	/** Kills the rdfStore process and the throwaway Postgres, and removes all temp directories. */
	def stop(): Unit =
		Try(storeProcess.stop())
		Try(pg.stop())
		Try(storeProcess.cleanup())
		Try(pg.cleanup())

end RemoteRdfStoreHarness


object RemoteRdfStoreHarness:

	private val httpClient = HttpClient.newHttpClient()

	def start(): RemoteRdfStoreHarness =
		val pg = PostgresFixture.start()
		try
			val store = StoreProcess.bootTwice(pgPort = pg.port)
			new RemoteRdfStoreHarness(pg, store)
		catch case e: Throwable =>
			Try(pg.stop())
			Try(pg.cleanup())
			throw e

	private def freePort(): Int =
		Using.resource(new ServerSocket()){ s =>
			s.setReuseAddress(true)
			s.bind(new InetSocketAddress("127.0.0.1", 0))
			s.getLocalPort
		}

	/** The classpath the forked rdfStore process needs to run `Main`. Since task 21 cut
	  * `meta.dependsOn(rdfStore)` (docs/rdf-common-split/21-remove-dependson.md), `meta`'s own
	  * test classloader no longer has rdfStore's classes on it, so this can no longer be recovered
	  * by walking the current JVM's classloader chain. Instead, `meta`'s build.sbt generates a
	  * `rdfstore-test-classpath.txt` test resource containing rdfStore's own
	  * `Compile / fullClasspath` (see the `Test / resourceGenerators` entry in the `meta` project's
	  * settings) - read that first. Fall back to the old classloader-walking trick (and finally to
	  * `java.class.path`) so this still degrades gracefully outside of sbt's normal resource
	  * pipeline. */
	private def currentClasspath(): String =
		def fromGeneratedResource(): Option[String] =
			Option(getClass.getResourceAsStream("/rdfstore-test-classpath.txt")).map: in =>
				try new String(in.readAllBytes(), StandardCharsets.UTF_8).trim
				finally in.close()

		def urls(cl: ClassLoader): List[java.net.URL] =
			if cl == null then Nil
			else cl match
				case u: java.net.URLClassLoader => u.getURLs.toList ++ urls(u.getParent)
				case other => urls(other.getParent)
		def fromClassloaderWalk(): Option[String] =
			val entries = urls(getClass.getClassLoader).map(_.getFile).distinct
			if entries.isEmpty then None else Some(entries.mkString(File.pathSeparator))

		fromGeneratedResource()
			.orElse(fromClassloaderWalk())
			.getOrElse(sys.props("java.class.path"))

	private def waitFor(what: String, timeout: FiniteDuration)(check: => Boolean): Unit =
		val deadline = System.nanoTime() + timeout.toNanos
		var ok = false
		while !ok && System.nanoTime() < deadline do
			ok = Try(check).getOrElse(false)
			if !ok then Thread.sleep(150)
		if !ok then throw new RuntimeException(s"Timed out waiting for: $what")

	private def httpGetStatus(uri: String): Int =
		val req = HttpRequest.newBuilder(URI.create(uri)).GET().timeout(5.seconds.toJava).build()
		httpClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode()

	/** Throwaway PostgreSQL cluster, created with `initdb`/started with `pg_ctl`. Trust auth on a
	  * loopback TCP port, so the credentials in `cpmeta.rdfLog.credentials` (whatever they are)
	  * are accepted without a real password. */
	final class PostgresFixture private (
		val port: Int,
		private val dataDir: Path,
		private val sockDir: Path
	):
		def stop(): Unit =
			runProcess("pg_ctl", "-D", dataDir.toString, "stop", "-m", "fast", "-w")

		def cleanup(): Unit =
			deleteRecursively(dataDir)
			deleteRecursively(sockDir)

	object PostgresFixture:
		def start(): PostgresFixture =
			val dataDir = Files.createTempDirectory("rdfstore-it-pgdata")
			// Postgres's Unix-domain socket path is limited to ~107 bytes; the default temp dir
			// (java.io.tmpdir, normally /tmp) is short enough, unlike this session's scratchpad path.
			val sockDir = Files.createTempDirectory("rdfstore-it-pgsock")
			val port = freePort()

			val initdb = runProcess(
				"initdb", "-D", dataDir.toString, "-U", "postgres", "-A", "trust",
				"--no-locale", "-E", "UTF8"
			)
			if initdb != 0 then throw new RuntimeException(s"initdb failed with exit code $initdb")

			val startExit = runProcess(
				"pg_ctl", "-D", dataDir.toString,
				"-o", s"-p $port -h 127.0.0.1 -k $sockDir",
				"-l", dataDir.resolve("postgres.log").toString,
				"start", "-w", "-t", "30"
			)
			if startExit != 0 then
				val log = Try(Files.readString(dataDir.resolve("postgres.log"))).getOrElse("<no log>")
				throw new RuntimeException(s"pg_ctl start failed with exit code $startExit; log:\n$log")

			new PostgresFixture(port, dataDir, sockDir)

	end PostgresFixture

	/** The forked `se.lu.nateko.cp.meta.rdfstore.Main` process, on a temp LMDB dir.
	  *
	  * rdfStore leaves a freshly built store read-only after its first boot (see
	  * `rdf-store-split.md:127` and `StorageSail.apply`'s `isFreshInit` check, which is true
	  * whenever the storage directory has no files yet). `bootTwice` performs the documented
	  * restart: boot once so the (empty) store and its index are created, stop, then boot again
	  * against the same now-non-empty directory, which makes the second boot writable. */
	final class StoreProcess private (
		val port: Int,
		private var process: Process,
		private val lmdbDir: Path,
		private val workDir: Path,
		private val classpath: String,
		private val pgPort: Int
	):
		def stop(): Unit =
			if process.isAlive then
				process.destroy()
				if !process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) then
					process.destroyForcibly()
					process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)

		def cleanup(): Unit =
			deleteRecursively(lmdbDir)
			deleteRecursively(workDir)

	object StoreProcess:

		private def launch(port: Int, lmdbDir: Path, workDir: Path, classpath: String, pgPort: Int): Process =
			val javaBin = Path.of(sys.props("java.home"), "bin", "java").toString
			// Overrides go through a real HOCON file rather than -D system properties: typesafe
			// config treats every system property as a plain string, and this app's config
			// loading (se.lu.nateko.cp.cpauth.core.ConfigLoader.parseAs, via spray-json) requires
			// the strict JSON type (JsNumber/JsBoolean), so e.g. `-DrdfStore.port=1234` would fail
			// to deserialize as the Int field it targets. A file gets real HOCON number/boolean
			// literals instead.
			val overridesFile = workDir.resolve("overrides.conf")
			Files.writeString(overridesFile,
				s"""cpmeta.rdfStorage.path = "${lmdbDir.toString.replace("\\", "\\\\")}"
				   |cpmeta.rdfStorage.recreateAtStartup = false
				   |cpmeta.rdfLog.server.host = "127.0.0.1"
				   |cpmeta.rdfLog.server.port = $pgPort
				   |rdfStore.httpBindInterface = "127.0.0.1"
				   |rdfStore.port = $port
				   |""".stripMargin
			)
			val cmd = java.util.List.of(
				javaBin, "-cp", classpath,
				s"-Dconfig.file=${overridesFile.toString}",
				"se.lu.nateko.cp.meta.rdfstore.Main"
			)
			val pb = new ProcessBuilder(cmd)
			pb.directory(workDir.toFile) // empty dir: no stray application.conf overrides the test config
			pb.redirectOutput(workDir.resolve("rdfstore-stdout.log").toFile)
			pb.redirectError(workDir.resolve("rdfstore-stderr.log").toFile)
			pb.start()

		private def awaitHealth(port: Int, process: Process, timeout: FiniteDuration): Unit =
			waitFor(s"rdfStore /health on port $port", timeout):
				if !process.isAlive then
					throw new RuntimeException(s"rdfStore process exited early with code ${process.exitValue()}")
				httpGetStatus(s"http://127.0.0.1:$port/health") == 200

		def bootTwice(pgPort: Int): StoreProcess =
			val lmdbDir = Files.createTempDirectory("rdfstore-it-lmdb")
			val workDir = Files.createTempDirectory("rdfstore-it-work")
			val classpath = currentClasspath()
			val port = freePort()

			// First boot: fresh, empty store. Builds the (empty) index, then goes read-only.
			val first = launch(port, lmdbDir, workDir, classpath, pgPort)
			try awaitHealth(port, first, 60.seconds)
			finally
				first.destroy()
				first.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)

			// Second boot, same directory: no longer "fresh", so the store is writable.
			val second = launch(port, lmdbDir, workDir, classpath, pgPort)
			awaitHealth(port, second, 60.seconds)

			new StoreProcess(port, second, lmdbDir, workDir, classpath, pgPort)

	end StoreProcess

	private def runProcess(cmd: String*): Int =
		val pb = new ProcessBuilder(cmd*)
		pb.redirectErrorStream(true)
		pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
		val proc = pb.start()
		// drain stdout so the process never blocks on a full pipe buffer
		val out = proc.getInputStream.readAllBytes()
		val finished = proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
		if !finished then
			proc.destroyForcibly()
			throw new RuntimeException(s"Command timed out: ${cmd.mkString(" ")}\n${new String(out)}")
		proc.exitValue()

	private def deleteRecursively(dir: Path): Unit =
		if Files.exists(dir) then
			Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.deleteIfExists(p))

	extension (d: FiniteDuration) private def toJava: java.time.Duration = java.time.Duration.ofMillis(d.toMillis)

end RemoteRdfStoreHarness
