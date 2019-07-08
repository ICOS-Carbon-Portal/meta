organization in ThisBuild := "se.lu.nateko.cp"
scalaVersion in ThisBuild := "2.12.8"

val commonScalacOptions = Seq(
	"-encoding", "UTF-8",
	"-unchecked",
	"-feature",
	"-deprecation",
	"-Xfuture",
	"-Yno-adapted-args",
	"-Ywarn-dead-code",
	"-Ywarn-numeric-widen",
	"-Ywarn-unused"
)
val jvmScalacOptions = commonScalacOptions :+ "-target:jvm-1.8"

lazy val metaCore = (project in file("core"))
	.settings(
		name := "meta-core",
		version := "0.4.1-SNAPSHOT",
		scalacOptions ++= jvmScalacOptions,
		libraryDependencies ++= Seq(
			"io.spray"              %% "spray-json"                         % "1.3.5",
			"org.scalatest"         %% "scalatest"                          % "3.0.1" % "test"
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

val akkaVersion = "2.5.17"
val akkaHttpVersion = "10.1.5"
val rdf4jVersion = "2.4.6"

val noGeronimo = ExclusionRule(organization = "org.apache.geronimo.specs")
val noJsonLd = ExclusionRule(organization = "com.github.jsonld-java")

val frontendBuild = taskKey[Unit]("Builds the front end apps")
frontendBuild := {
	import scala.sys.process.Process
	(Process("npm install") #&& Process("npm run gulp")).!
}

lazy val meta = (project in file("."))
	.dependsOn(metaCore)
	.enablePlugins(SbtTwirl,IcosCpSbtDeployPlugin)
	.settings(
		name := "meta",
		version := "0.4.4",
		scalacOptions ++= jvmScalacOptions,

		libraryDependencies ++= Seq(
			"com.typesafe.akka"     %% "akka-http-spray-json"               % akkaHttpVersion,
			"com.typesafe.akka"     %% "akka-stream"                        % akkaVersion,
			"com.typesafe.akka"     %% "akka-slf4j"                         % akkaVersion,
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
			"org.apache.commons"     % "commons-email"                      % "1.4",
			"se.lu.nateko.cp"       %% "views-core"                         % "0.4.1-SNAPSHOT",
			"se.lu.nateko.cp"       %% "cpauth-core"                        % "0.6.0-SNAPSHOT",
			"org.scalatest"         %% "scalatest"                          % "3.0.1" % "test"
		),
		cpDeployTarget := "cpmeta",
		cpDeployBuildInfoPackage := "se.lu.nateko.cp.meta",

		scalacOptions += "-Ywarn-unused-import:false",

		assemblyMergeStrategy in assembly := {
			case PathList("META-INF", "axiom.xml") => MergeStrategy.first
			case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.properties") => MergeStrategy.first
			case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.xml") => MergeStrategy.first
			case PathList("org", "apache", "commons", "logging", _*) => MergeStrategy.first
			case "application.conf" => MergeStrategy.concat
			case PathList(name) if name.contains("-fastopt.js") => MergeStrategy.discard
			case x => ((assemblyMergeStrategy in assembly).value)(x)
			//case PathList(ps @ _*) if(ps.exists(_.contains("guava")) && ps.last == "pom.xml") => {println(ps); MergeStrategy.first}
		},

		assembledMappings.in(assembly) += {
			val finalJsFile = fullOptJS.in(uploadgui, Compile).value.data
			frontendBuild.value
			sbtassembly.MappingSet(None, Vector(finalJsFile -> finalJsFile.getName))
		},

		resources.in(Compile) ++= {
			val jsFile = fastOptJS.in(uploadgui, Compile).value.data
			val srcMap = new java.io.File(jsFile.getAbsolutePath + ".map")
			Seq(jsFile, srcMap)
		},

		watchSources ++= watchSources.in(uploadgui, Compile).value,

		initialCommands in console in Test := """
			import se.lu.nateko.cp.meta.Drought2018._
			import se.lu.nateko.cp.meta.UploadWorkbench._
		""",

		cleanupCommands in console in Test := """
			system.terminate()
		"""
	)

lazy val uploadgui = (project in file("uploadgui"))
	.enablePlugins(ScalaJSPlugin)
	.settings(
		name := "uploadgui",
		version := "0.1.1",
		scalacOptions ++= commonScalacOptions,

		scalaJSUseMainModuleInitializer := true,

		libraryDependencies ++= Seq(
			"org.scala-js"      %%% "scalajs-dom"       % "0.9.6",
			"com.typesafe.play" %%% "play-json"         % "2.6.7",
			"org.scala-js"      %%% "scalajs-java-time" % "0.2.4",
			"org.scalatest"     %%% "scalatest"         % "3.0.4" % "test"
		)
	)

/*
lazy val jobAd = (project in file("jobAd"))
	.enablePlugins(SbtTwirl)
	.settings(
		name := "jobAd",
		version := "1.0",
		scalacOptions ++= jvmScalacOptions,
		libraryDependencies ++= Seq(
			"com.typesafe.akka"     %% "akka-http-spray-json-experimental"  % akkaVersion,
			"com.typesafe.akka"     %% "akka-slf4j"                         % akkaVersion,
			"com.fasterxml.uuid"     % "java-uuid-generator"                % "3.1.4",
			"ch.qos.logback"         % "logback-classic"                    % "1.1.3",
			"se.lu.nateko.cp"       %% "views-core"                         % "0.1-SNAPSHOT"
		)
	)
*/
