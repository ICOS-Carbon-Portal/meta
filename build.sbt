lazy val commonSettings = Seq(
	organization := "se.lu.nateko.cp",
	scalaVersion := "2.11.8",

	scalacOptions ++= Seq(
		"-unchecked",
		"-deprecation",
		"-Xlint",
		"-Ywarn-dead-code",
		"-language:_",
		"-target:jvm-1.8",
		"-encoding", "UTF-8"
	)
)

lazy val metaCore = (project in file("core"))
	.settings(commonSettings: _*)
	.settings(
		name := "meta-core",
		version := "0.2.0-SNAPSHOT",
		libraryDependencies ++= Seq(
			"io.spray" %% "spray-json" % "1.3.2"
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

lazy val views = (project in file("views"))
	.dependsOn(metaCore)
	.settings(commonSettings: _*)
	.enablePlugins(SbtTwirl)
	.settings(
		name := "meta-views",
		version := "0.1.0-SNAPSHOT",
		libraryDependencies += "se.lu.nateko.cp" %% "views-core" % "0.1-SNAPSHOT"
	)

val akkaVersion = "2.4.8"
val sesameVersion = "2.8.7"
val noGeronimo = ExclusionRule(organization = "org.apache.geronimo.specs")

lazy val meta = (project in file("."))
	.dependsOn(metaCore)
	.dependsOn(views)
	.settings(commonSettings: _*)
	.settings(
		name := "meta",
		version := "0.2",

		libraryDependencies ++= Seq(
			"com.typesafe.akka"     %% "akka-http-spray-json-experimental"  % akkaVersion,
			"com.typesafe.akka"     %% "akka-slf4j"                         % akkaVersion,
			"ch.qos.logback"         % "logback-classic"                    % "1.1.3",
			"org.openrdf.sesame"     % "sesame-repository-sail"             % sesameVersion,
			"org.openrdf.sesame"     % "sesame-sail-memory"                 % sesameVersion,
			"org.openrdf.sesame"     % "sesame-queryresultio-sparqljson"    % sesameVersion,
			"org.openrdf.sesame"     % "sesame-queryresultio-text"          % sesameVersion,
			"org.postgresql"         % "postgresql"                         % "9.4-1201-jdbc41",
			"net.sourceforge.owlapi" % "org.semanticweb.hermit"             % "1.3.8.413" excludeAll(noGeronimo),
			"se.lu.nateko.cp"       %% "cpauth-core"                        % "0.4-SNAPSHOT",
			"org.apache.commons"     % "commons-email"                      % "1.4",
			"org.scalatest"          % "scalatest_2.11"                     % "2.2.1" % "test"
		),

		assemblyMergeStrategy in assembly := {
			case PathList("META-INF", "axiom.xml") => MergeStrategy.first
			case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.properties") => MergeStrategy.first
			case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.xml") => MergeStrategy.first
			case PathList("org", "apache", "commons", "logging", _*) => MergeStrategy.first
			case "application.conf" => MergeStrategy.concat
			case x => ((assemblyMergeStrategy in assembly).value)(x)
			//case PathList(ps @ _*) if(ps.exists(_.contains("guava")) && ps.last == "pom.xml") => {println(ps); MergeStrategy.first}
		},

		initialCommands in console := """
			import se.lu.nateko.cp.meta.Playground._
		""",

		cleanupCommands in console := """
			stop()
		"""
	)

lazy val jobAd = (project in file("jobAd"))
	.settings(commonSettings: _*)
	.enablePlugins(SbtTwirl)
	.settings(
		name := "jobAd",
		version := "1.0",
		libraryDependencies ++= Seq(
			"com.typesafe.akka"     %% "akka-http-spray-json-experimental"  % akkaVersion,
			"com.typesafe.akka"     %% "akka-slf4j"                         % akkaVersion,
			"com.fasterxml.uuid"     % "java-uuid-generator"                % "3.1.4",
			"ch.qos.logback"         % "logback-classic"                    % "1.1.3",
			"se.lu.nateko.cp"       %% "views-core"                         % "0.1-SNAPSHOT"
		)
	)

