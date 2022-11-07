import sbt.librarymanagement.InclExclRule
Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / organization := "se.lu.nateko.cp"
ThisBuild / scalaVersion := "3.2.0"

val commonScalacOptions = Seq(
	"-encoding", "UTF-8",
	"-unchecked",
	"-feature",
	"-deprecation"
)

lazy val metaCore = (project in file("core"))
	.enablePlugins(IcosCpSbtTsGenPlugin)
	.settings(
		name := "meta-core",
		version := "0.7.5",
		scalacOptions ++= commonScalacOptions,
		libraryDependencies ++= Seq(
			"io.spray"              %% "spray-json"                         % "1.3.6",
			"org.scalatest"         %% "scalatest"                          % "3.2.11" % "test"
		),
		cpTsGenTypeMap := Map(
			"URI" -> "string",
			"Instant" -> "string",
			"LocalDate" -> "string",
			"Sha256Sum" -> "string",
			"Orcid" -> "string",
			"JsValue" -> "object",
			"CountryCode" -> "string"
		),
		cpTsGenSources := {
			val dir = (Compile / scalaSource).value / "se" / "lu" / "nateko" / "cp" / "meta" / "core" / "data"
			Seq(
				dir / "GeoFeatures.scala", dir / "TemporalFeatures.scala", dir / "DataItem.scala", dir / "DataObject.scala",
				dir / "Station.scala", dir / "package.scala"
			)
		},
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
val rdf4jVersion = "4.0.1"
val owlApiVersion = "5.1.20"

val noGeronimo = ExclusionRule(organization = "org.apache.geronimo.specs")
val noOwlApiDistr = ExclusionRule("net.sourceforge.owlapi", "owlapi-distribution")

val frontendBuild = taskKey[Unit]("Builds the front end apps")
frontendBuild := {
	import scala.sys.process.Process
	val log = streams.value.log
	val exitCode = (Process("npm ci") #&& Process("npm run gulp")).!
	if(exitCode == 0) log.info("Front end build was successfull")
	else sys.error("Front end build error")
}

val fetchGCMDKeywords = taskKey[Unit]("Fetches GCMD keywords from NASA")
fetchGCMDKeywords := {
	import scala.sys.process._
	val log = streams.value.log
	val exitCode = (
		url("https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/sciencekeywords/?format=json") #>
		Seq("jq", ".concepts | map(.prefLabel)") #>
		file("./src/main/resources/gcmdkeywords.json")
	).!
	if(exitCode == 0) log.info("Fetched GCMD keywords list")
	else sys.error("Error while fetching GCMD keywords list")
}

lazy val meta = (project in file("."))
	.dependsOn(metaCore)
	.enablePlugins(SbtTwirl,IcosCpSbtDeployPlugin)
	.settings(
		name := "meta",
		version := "0.7.0",
		scalacOptions ++= commonScalacOptions,

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
			"org.eclipse.rdf4j"      % "rdf4j-rio-rdfxml"                   % rdf4jVersion,
			"org.eclipse.rdf4j"      % "rdf4j-queryresultio-sparqljson"     % rdf4jVersion,
			"org.eclipse.rdf4j"      % "rdf4j-queryresultio-text"           % rdf4jVersion,
			//"org.eclipse.rdf4j"      % "rdf4j-queryalgebra-geosparql"       % rdf4jVersion,
			"org.postgresql"         % "postgresql"                         % "9.4-1201-jdbc41",
			"net.sourceforge.owlapi" % "org.semanticweb.hermit"             % "1.4.5.519" excludeAll(noOwlApiDistr, noGeronimo),
			"net.sourceforge.owlapi" % "owlapi-apibinding"                  % owlApiVersion excludeAll(InclExclRule.everything),
			"net.sourceforge.owlapi" % "owlapi-impl"                        % owlApiVersion,
			"net.sourceforge.owlapi" % "owlapi-parsers"                     % owlApiVersion,
			"com.sun.mail"           % "jakarta.mail"                       % "1.6.7",
			"org.roaringbitmap"      % "RoaringBitmap"                      % "0.9.27",
			"com.esotericsoftware"   % "kryo"                               % "5.3.0",
			"se.lu.nateko.cp"       %% "views-core"                         % "0.6.4",
			"se.lu.nateko.cp"       %% "cpauth-core"                        % "0.7.0",
			"se.lu.nateko.cp"       %% "doi-core"                           % "0.4.0",
			"com.github.workingDog" %% "scalakml"                           % "1.5"           % "test" exclude("org.scala-lang.modules", "scala-xml_2.13") cross CrossVersion.for3Use2_13,
			"com.typesafe.akka"     %% "akka-http-testkit"                  % akkaHttpVersion % "test" excludeAll("io.spray") cross CrossVersion.for3Use2_13,
			"com.typesafe.akka"     %% "akka-stream-testkit"                % akkaVersion     % "test" cross CrossVersion.for3Use2_13,
			"org.scalatest"         %% "scalatest"                          % "3.2.11"        % "test"
		),

		cpDeployTarget := "cpmeta",
		cpDeployBuildInfoPackage := "se.lu.nateko.cp.meta",
		cpDeployPreAssembly := Def.sequential(metaCore / Test / test, Test / test, frontendBuild, fetchGCMDKeywords).value,

		assembly / assemblyMergeStrategy := {
			case PathList("META-INF", "axiom.xml") => MergeStrategy.first
			case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.properties") => MergeStrategy.first
			case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.xml") => MergeStrategy.first
			case PathList("org", "apache", "commons", "logging", _*) => MergeStrategy.first
			case "application.conf" => MergeStrategy.concat
			case "module-info.class" => MergeStrategy.discard
			case PathList(name) if name.contains("-fastopt.js") => MergeStrategy.discard
			case x => ((assembly / assemblyMergeStrategy).value)(x)
			//case PathList(ps @ _*) if(ps.exists(_.contains("guava")) && ps.last == "pom.xml") => {println(ps); MergeStrategy.first}
		},

		assembly / assembledMappings += {
			val finalJsFile = (uploadgui / Compile / fullOptJS).value.data
			sbtassembly.MappingSet(None, Vector(finalJsFile -> finalJsFile.getName))
		},

		Compile / resources ++= {
			val jsFile = (uploadgui / Compile / fastOptJS).value.data
			val srcMap = new java.io.File(jsFile.getAbsolutePath + ".map")
			Seq(jsFile, srcMap)
		},

		watchSources ++= (uploadgui / Compile / watchSources).value,

		reStart / aggregate := false,

		Test / console / initialCommands := {
			//import se.lu.nateko.cp.meta.upload.UploadWorkbench.{given, *}
			"""import se.lu.nateko.cp.meta.test.Playground.{given, *}"""
		},

		Test / console / cleanupCommands := "system.terminate()"
	)

lazy val uploadgui = (project in file("uploadgui"))
	.enablePlugins(ScalaJSPlugin)
	.settings(
		name := "uploadgui",
		version := "0.1.3",
		scalacOptions ++= commonScalacOptions,

		scalaJSUseMainModuleInitializer := true,

		libraryDependencies ++= Seq(
			"org.scala-js"      %%% "scalajs-dom"       % "2.1.0",
			"io.github.cquiroz" %%% "scala-java-time"   % "2.3.0",
			"com.typesafe.play" %%% "play-json"         % "2.10.0-RC6",
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
