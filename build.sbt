name := "cpmeta"

version := "0.1"

scalaVersion := "2.11.7"

val sesameVersion = "2.7.12"

libraryDependencies ++= Seq(
	"com.typesafe.akka"  %% "akka-http-core-experimental"        % "1.0",
	"com.typesafe.akka"  %% "akka-http-spray-json-experimental"  % "1.0",
	"ch.qos.logback"     %  "logback-classic"  % "1.1.2",
	"net.sourceforge.owlapi" % "owlapi-distribution"     % "4.0.2",
	"org.openrdf.sesame"     % "sesame-repository-sail"          % sesameVersion,
	"org.openrdf.sesame"     % "sesame-sail-memory"              % sesameVersion,
	"org.openrdf.sesame"     % "sesame-queryresultio-sparqljson" % sesameVersion,
	"org.postgresql"         % "postgresql"              % "9.4-1201-jdbc41",
	"dk.brics.automaton"          % "automaton"   % "1.11-8", //Hermit
	"org.apache.ws.commons.axiom" % "axiom-api"   % "1.2.14", //Hermit
	"org.apache.ws.commons.axiom" % "axiom-c14n"  % "1.2.14", //Hermit
	"org.apache.ws.commons.axiom" % "axiom-dom"   % "1.2.14", //Hermit
	"org.apache.ws.commons.axiom" % "axiom-impl"  % "1.2.14",  //Hermit
	"se.lu.nateko.cp"    %% "cpauth-core"       % "0.2",
	"org.scalatest"      %  "scalatest_2.11"   % "2.2.1" % "test"
)

assemblyMergeStrategy in assembly := {
	case PathList("META-INF", "axiom.xml") => MergeStrategy.first
	case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.properties") => MergeStrategy.first
	case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.xml") => MergeStrategy.first
	case PathList("org", "apache", "commons", "logging", _*) => MergeStrategy.first
	//case PathList(ps @ _*) if(ps.exists(_.contains("guava")) && ps.last == "pom.xml") => {println(ps); MergeStrategy.first}
	case "application.conf" => MergeStrategy.concat
	case x => ((assemblyMergeStrategy in assembly).value)(x)
}

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.8",
  "-encoding", "UTF-8"
)

initialCommands in console := """
import se.lu.nateko.cp.meta.ingestion._
"""

Revolver.settings
