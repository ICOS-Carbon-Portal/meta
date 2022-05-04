Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / organization := "se.lu.nateko.cp"
ThisBuild / scalaVersion := "3.1.1"

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
		version := "0.7.3",
		scalacOptions ++= commonScalacOptions,
		libraryDependencies ++= Seq(
			"io.spray"              %% "spray-json"                         % "1.3.6" cross CrossVersion.for3Use2_13,
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
val rdf4jVersion = "2.4.6"

val noGeronimo = ExclusionRule(organization = "org.apache.geronimo.specs")
val noJsonLd = ExclusionRule(organization = "com.github.jsonld-java")

val frontendBuild = taskKey[Int]("Builds the front end apps")
frontendBuild := {
	import scala.sys.process.Process
	(Process("npm ci") #&& Process("npm run gulp")).!
}

val fetchGCMDKeywords = taskKey[Int]("Fetches GCMD keywords from NASA")
fetchGCMDKeywords := {
	import scala.sys.process._
	url("https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/sciencekeywords/?format=json") #>
		Seq("jq", ".concepts | map(.prefLabel)") #>
		file("./src/main/resources/gcmdkeywords.json") !
}

lazy val meta = (project in file("."))
	.dependsOn(metaCore)
	.enablePlugins(SbtTwirl,IcosCpSbtDeployPlugin)
	.settings(
		name := "meta",
		version := "0.7.0",
		scalacOptions ++= commonScalacOptions,

		libraryDependencies := {
			libraryDependencies.value.map{
				case m if m.name.startsWith("twirl-api") =>
					m.cross(CrossVersion.for3Use2_13)
				case m => m
			}
		},

		libraryDependencies ++= Seq(
			"com.typesafe.akka"     %% "akka-http-spray-json"               % akkaHttpVersion cross CrossVersion.for3Use2_13,
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
			"org.eclipse.rdf4j"      % "rdf4j-queryalgebra-geosparql"       % rdf4jVersion,
			"org.postgresql"         % "postgresql"                         % "9.4-1201-jdbc41",
			"net.sourceforge.owlapi" % "org.semanticweb.hermit"             % "1.3.8.510" excludeAll(noGeronimo, noJsonLd),
			"com.sun.mail"           % "javax.mail"                         % "1.6.2",
			"org.roaringbitmap"      % "RoaringBitmap"                      % "0.8.11",
			"se.lu.nateko.cp"       %% "views-core"                         % "0.5.0" cross CrossVersion.for3Use2_13,
			"se.lu.nateko.cp"       %% "cpauth-core"                        % "0.6.5" cross CrossVersion.for3Use2_13,
			"se.lu.nateko.cp"       %% "doi-common"                         % "0.2.0" cross CrossVersion.for3Use2_13,
			"se.lu.nateko.cp"       %% "doi-core"                           % "0.2.0" cross CrossVersion.for3Use2_13,
			"com.github.workingDog" %% "scalakml"                           % "1.5"           % "test" cross CrossVersion.for3Use2_13,
			"com.typesafe.akka"     %% "akka-http-testkit"                  % akkaHttpVersion % "test" cross CrossVersion.for3Use2_13,
			"com.typesafe.akka"     %% "akka-stream-testkit"                % akkaVersion     % "test" cross CrossVersion.for3Use2_13,
			"org.scalatest"         %% "scalatest"                          % "3.2.11"        % "test" exclude("org.scala-lang.modules", "scala-xml_3")
		),

		Test / test := {
			val _ = (metaCore / Test / test).value
			(Test / test).value
		},

		cpDeployTarget := "cpmeta",
		cpDeployBuildInfoPackage := "se.lu.nateko.cp.meta",

		assembly / assemblyMergeStrategy := {
			case PathList("META-INF", "axiom.xml") => MergeStrategy.first
			case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.properties") => MergeStrategy.first
			case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.xml") => MergeStrategy.first
			case PathList("org", "apache", "commons", "logging", _*) => MergeStrategy.first
			case "application.conf" => MergeStrategy.concat
			case PathList(name) if name.contains("-fastopt.js") => MergeStrategy.discard
			case x => ((assembly / assemblyMergeStrategy).value)(x)
			//case PathList(ps @ _*) if(ps.exists(_.contains("guava")) && ps.last == "pom.xml") => {println(ps); MergeStrategy.first}
		},

		assembly / assembledMappings += {
			val finalJsFile = (uploadgui / Compile / fullOptJS).value.data
			sbtassembly.MappingSet(None, Vector(finalJsFile -> finalJsFile.getName))
		},

		assembly := Def.taskDyn{
			val original = assembly.taskValue
			if(frontendBuild.value != 0) throw new IllegalStateException("Front end build error")
			else if(fetchGCMDKeywords.value != 0) throw new IllegalStateException("Error while fetching GCMD keywords list")
			else Def.task(original.value)
		}.value,

		Compile / resources ++= {
			val jsFile = (uploadgui / Compile / fastOptJS).value.data
			val srcMap = new java.io.File(jsFile.getAbsolutePath + ".map")
			Seq(jsFile, srcMap)
		},

		watchSources ++= (uploadgui / Compile / watchSources).value,

		reStart / aggregate := false,

		Test / console / initialCommands := """
			import se.lu.nateko.cp.meta.upload.UploadWorkbench._
		""",

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
			"se.lu.nateko.cp"   %%% "doi-common"        % "0.2.0" cross CrossVersion.for3Use2_13,
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
