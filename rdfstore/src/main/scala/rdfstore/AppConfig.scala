package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import com.typesafe.config.{Config, ConfigFactory}

/**
 * Assembles the application configuration for both `meta` and the standalone
 * `rdfStore` service. Owned by this package (rather than inherited from
 * cpauth-core) because the classpath defaults are shipped with the rdfStore
 * service
 *
 * The layering is explicit and documented, highest priority first:
 *
 *   1. JVM system properties (`-Dcpmeta.port=…`)
 *   2. `application.conf` in the JVM's *working directory*, if present.
 *      This is the environment-specific file, kept out of version control;
 *      see `example.application.conf` in the project root.
 *   3. `application.conf` from the classpath (the defaults shipped in
 *      rdfstore/src/main/resources)
 *   4. the `reference.conf`s of the dependencies (meta-core, cpauth-core, akka, …)
 *
 * Setting `-Dconfig.file`, `-Dconfig.resource` or `-Dconfig.url` disables the
 * working-directory lookup entirely and defers to plain Typesafe Config
 * semantics, so an explicitly named file is never outranked by a stray
 * `./application.conf`.
 */
object AppConfig:

	private val explicitConfigProps = Seq("config.file", "config.resource", "config.url")

	/**
	 * The complete root config of the running JVM (all sections: `cpmeta`,
	 * `rdfStore`, `akka`, …), obtained by overlaying the working directory's
	 * `application.conf` on the classpath defaults as described above.
	 */
	lazy val rootConfWithWorkingDirOverrides: Config =
		val cwdConf = new java.io.File("application.conf").getAbsoluteFile
		if explicitConfigProps.exists(sys.props.contains) || !cwdConf.exists then
			ConfigFactory.load()
		else
			// the reference layer must stay unresolved until after the merge, so that
			// substitutions like ${authPub} and ${cpauthCore.mailing} see the overrides
			ConfigFactory.defaultOverrides()
				.withFallback(ConfigFactory.parseFile(cwdConf))
				.withFallback(ConfigFactory.defaultApplication())
				.withFallback(ConfigFactory.defaultReferenceUnresolved())
				.resolve()

end AppConfig
