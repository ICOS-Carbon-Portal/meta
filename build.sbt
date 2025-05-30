import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import sbt.librarymanagement.InclExclRule
Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / organization := "se.lu.nateko.cp"
ThisBuild / scalaVersion := "3.3.4"

val commonScalacOptions = Seq(
	"-encoding", "UTF-8",
	"-unchecked",
	"-feature",
	"-deprecation",
	"-Werror",
	"-Wunused:imports"
)

lazy val metaCore = (project in file("core"))
	.enablePlugins(IcosCpSbtCodeGenPlugin)
	.settings(
		name := "meta-core",
		version := "0.7.21",
		scalacOptions ++= commonScalacOptions,
		libraryDependencies ++= Seq(
			"io.spray"              %% "spray-json"                         % "1.3.6",
			"eu.icoscp"             %% "envri"                              % "0.1.0",
			"se.lu.nateko.cp"       %% "doi-core"                           % "0.4.4",
			"se.lu.nateko.cp"       %% "cpauth-core"                        % "0.10.1",
			"org.roaringbitmap"      % "RoaringBitmap"                      % "0.9.45",
			"org.scalatest"         %% "scalatest"                          % "3.2.11" % "test"
		),
		cpTsGenTypeMap := Map(
			"URI" -> "string",
			"Instant" -> "string",
			"LocalDate" -> "string",
			"Sha256Sum" -> "string",
			"Orcid" -> "string",
			"JsValue" -> "object",
			"DoiMeta" -> "object",
			"CountryCode" -> "string",
		),
		cpCodeGenSources := {
			val dir = (Compile / scalaSource).value / "se" / "lu" / "nateko" / "cp" / "meta" / "core" / "data"
			Seq(
				dir / "GeoFeatures.scala", dir / "TemporalFeatures.scala", dir / "DataItem.scala", dir / "DataObject.scala",
				dir / "Station.scala", dir / "Instrument.scala", dir / "package.scala"
			)
		},
		cpPyGenTypeMap := Map(
			"URI" -> "str",
			"Instant" -> "str",
			"LocalDate" -> "str",
			"Sha256Sum" -> "str",
			"Orcid" -> "str",
			"JsValue" -> "object",
			"DoiMeta" -> "object",
			"CountryCode" -> "str"
		),
		publishTo := {
			val nexus = "https://repo.icos-cp.eu/content/repositories/"
			if (isSnapshot.value)
				Some("snapshots" at nexus + "snapshots")
			else
				Some("releases"  at nexus + "releases")
		},
		credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
	)

val akkaVersion = "2.6.18"
val akkaHttpVersion = "10.2.8"
val rdf4jVersion = "5.0.2"
val owlApiVersion = "5.1.20"

val noGeronimo = ExclusionRule(organization = "org.apache.geronimo.specs")
val noOwlApiDistr = ExclusionRule("net.sourceforge.owlapi", "owlapi-distribution")

val frontendBuild = taskKey[Unit]("Builds the front end apps")
frontendBuild := {
	import scala.sys.process.Process
	val log = streams.value.log
	val targetDir = (Compile / classDirectory).value.getAbsolutePath
	val exitCode = (Process("npm ci") #&& Process(s"npm run gulp -- --target=$targetDir")).!
	if(exitCode == 0) log.info("Front end build was successfull")
	else sys.error("Front end build error")
}

val fetchGCMDKeywords = taskKey[Unit]("Fetches GCMD keywords from NASA")
fetchGCMDKeywords := {
	import scala.sys.process._
	val log = streams.value.log
	val jsonPath = "./src/main/resources/gcmdkeywords.json"
	val tmpFile = file(jsonPath + "~")
	val exitCode = (
		url("https://gcmd.earthdata.nasa.gov/kms/tree/concept_scheme/sciencekeywords") #>
		Seq("jq", "[.tree.treeData[].children[].children[] | .. | objects | .title]") #>
		tmpFile
	).!
	if(exitCode == 0) {
		Files.move(tmpFile.toPath, file(jsonPath).toPath, REPLACE_EXISTING)
		log.info("Fetched GCMD keywords list")
	} else log.error(
		"Error while fetching GCMD keywords list! Check that the machine " +
		s"you are deploying from has an older file at $jsonPath"
	)
}

lazy val meta = (project in file("."))
	.dependsOn(metaCore)
	.enablePlugins(SbtTwirl,IcosCpSbtDeployPlugin)
	.settings(
		name := "meta",
		version := "0.11.0",
		scalacOptions ++= (commonScalacOptions ++ Seq("-Wconf:src=.*(html|xml):s")),

		excludeDependencies ++= Seq(
			ExclusionRule("com.github.jsonld-java", "jsonld-java"),
			ExclusionRule("jakarta.activation", "jakarta.activation-api"),
		),

		libraryDependencies ++= Seq(
			"com.typesafe.akka"     %% "akka-http-spray-json"               % akkaHttpVersion excludeAll("io.spray") cross CrossVersion.for3Use2_13,
			"com.typesafe.akka"     %% "akka-http-caching"                  % akkaHttpVersion cross CrossVersion.for3Use2_13,
			"com.typesafe.akka"     %% "akka-stream"                        % akkaVersion cross CrossVersion.for3Use2_13,
			"com.typesafe.akka"     %% "akka-slf4j"                         % akkaVersion cross CrossVersion.for3Use2_13,
			"ch.qos.logback"         % "logback-classic"                    % "1.1.3",
			"org.eclipse.rdf4j"      % "rdf4j-repository-sail"              % rdf4jVersion,
			"org.eclipse.rdf4j"      % "rdf4j-sail-memory"                  % rdf4jVersion,
			"org.eclipse.rdf4j"      % "rdf4j-sail-nativerdf"               % rdf4jVersion,
			"org.eclipse.rdf4j"      % "rdf4j-sail-lmdb"                    % rdf4jVersion,
			"org.eclipse.rdf4j"      % "rdf4j-rio-rdfxml"                   % rdf4jVersion,
			"org.eclipse.rdf4j"      % "rdf4j-queryresultio-sparqljson"     % rdf4jVersion,
			"org.eclipse.rdf4j"      % "rdf4j-queryresultio-text"           % rdf4jVersion,
			//"org.eclipse.rdf4j"      % "rdf4j-queryalgebra-geosparql"       % rdf4jVersion,
			"org.lwjgl"              % "lwjgl"                              % "3.3.4",
			"org.lwjgl"              % "lwjgl-lmdb"                         % "3.3.4",
			"org.postgresql"         % "postgresql"                         % "42.6.0",
			"net.sourceforge.owlapi" % "org.semanticweb.hermit"             % "1.4.5.519" excludeAll(noOwlApiDistr, noGeronimo),
			"net.sourceforge.owlapi" % "owlapi-apibinding"                  % owlApiVersion excludeAll(InclExclRule.everything),
			"net.sourceforge.owlapi" % "owlapi-impl"                        % owlApiVersion,
			"net.sourceforge.owlapi" % "owlapi-parsers"                     % owlApiVersion,
			"com.sun.mail"           % "jakarta.mail"                       % "1.6.7",
			"com.esotericsoftware"   % "kryo"                               % "5.6.0",
			"se.lu.nateko.cp"       %% "views-core"                         % "0.7.16",
			"se.lu.nateko.cp"       %% "doi-core"                           % "0.4.4",
			"com.github.workingDog" %% "scalakml"                           % "1.5"           % "test" exclude("org.scala-lang.modules", "scala-xml_2.13") cross CrossVersion.for3Use2_13,
			"com.typesafe.akka"     %% "akka-http-testkit"                  % akkaHttpVersion % "test" excludeAll("io.spray") cross CrossVersion.for3Use2_13,
			"com.typesafe.akka"     %% "akka-stream-testkit"                % akkaVersion     % "test" cross CrossVersion.for3Use2_13,
			"org.scalatest"         %% "scalatest"                          % "3.2.11"        % "test",
			"org.locationtech.jts"   % "jts-core"                           % "1.19.0",
			"org.locationtech.jts.io" % "jts-io-common"                     % "1.19.0",
			"org.commonmark"        % "commonmark"                          % "0.24.0",
			"org.commonmark"        % "commonmark-ext-autolink"             % "0.24.0"
		),

		cpDeployTarget := "cpmeta",
		cpDeployBuildInfoPackage := "se.lu.nateko.cp.meta",
		cpDeployPreAssembly := Def.sequential(
			metaCore / clean,
			uploadgui / clean,
			clean,
			metaCore / Test / test,
			Test / test,
			frontendBuild,
			fetchGCMDKeywords
		).value,
		cpDeployPlaybook := "core.yml",
		cpDeployPermittedInventories := Some(Seq("production", "staging", "cities")),
		cpDeployInfraBranch := "master",

		Compile / unmanagedResources ++= {
			val finalJsFile = (uploadgui / Compile / fullOptJS).value.data
			val mapJsFile = new java.io.File(finalJsFile.getAbsolutePath + ".map")
			Vector(finalJsFile, mapJsFile)
		},

		assembly / assemblyMergeStrategy := {
			case PathList("META-INF", "axiom.xml") => MergeStrategy.first
			case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.properties") => MergeStrategy.first
			case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.xml") => MergeStrategy.first
			case PathList("org", "apache", "commons", "logging", _*) => MergeStrategy.first
			case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
			case PathList(name) if name.contains("-fastopt.js") => MergeStrategy.discard
			case "application.conf" => MergeStrategy.concat
			case x => ((assembly / assemblyMergeStrategy).value)(x)
			//case PathList(ps @ _*) if(ps.exists(_.contains("guava")) && ps.last == "pom.xml") => {println(ps); MergeStrategy.first}
		},

		assembly / assemblyRepeatableBuild := false,

		Compile / resources ++= {
			val jsFile = (uploadgui / Compile / fastOptJS).value.data
			val srcMap = new java.io.File(jsFile.getAbsolutePath + ".map")
			Seq(jsFile, srcMap)
		},

		watchSources ++= (uploadgui / Compile / watchSources).value,

		reStart / aggregate := false,

		// Test / console / initialCommands := {
		// 	"""import se.lu.nateko.cp.meta.upload.UploadWorkbench.{given, *}"""
			//"""import se.lu.nateko.cp.meta.test.Playground.{given, *}"""
		// },

		//Test / console / cleanupCommands := "system.terminate()"
	)

lazy val uploadgui = (project in file("uploadgui"))
	.enablePlugins(ScalaJSPlugin)
	.settings(
		name := "uploadgui",
		version := "0.1.3",
		scalacOptions ++= commonScalacOptions,

		scalaJSUseMainModuleInitializer := true,

		libraryDependencies ++= Seq(
			"org.scala-js"      %%% "scalajs-dom"       % "2.8.0",
			"eu.icoscp"         %%% "envri"             % "0.1.0",
			"io.github.cquiroz" %%% "scala-java-time"   % "2.3.0",
			"com.typesafe.play" %%% "play-json"         % "2.10.0-RC7",
			"se.lu.nateko.cp"   %%% "doi-common"        % "0.4.0",
			"org.scalatest"     %%% "scalatest"         % "3.2.11" % "test"
		)
	)

/*
lazy val jobAd = (project in file("jobAd"))
	.enablePlugins(SbtTwirl)
	.settings(
		name := "jobAd",
		version := "1.0",
		scalacOptions ++= commonScalacOptions,
		libraryDependencies ++= Seq(
			"com.typesafe.akka"     %% "akka-http-spray-json-experimental"  % akkaVersion,
			"com.typesafe.akka"     %% "akka-slf4j"                         % akkaVersion,
			"com.fasterxml.uuid"     % "java-uuid-generator"                % "3.1.4",
			"ch.qos.logback"         % "logback-classic"                    % "1.1.3",
			"se.lu.nateko.cp"       %% "views-core"                         % "0.1-SNAPSHOT"
		)
	)
*/
