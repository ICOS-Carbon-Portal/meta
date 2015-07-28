name := "cpmeta"

version := "0.1"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
	"io.spray"           %% "spray-can"        % "1.3.3",
	"io.spray"           %% "spray-routing"    % "1.3.3",
	"io.spray"           %% "spray-json"       % "1.3.2",
	"com.typesafe.akka"  %% "akka-actor"       % "2.3.9",
	"com.typesafe.akka"  %% "akka-slf4j"       % "2.3.9",
	"ch.qos.logback"     %  "logback-classic"  % "1.1.2",
	"org.scalatest"      %  "scalatest_2.11"   % "2.2.1" % "test",
	"net.sourceforge.owlapi" % "owlapi-distribution"     % "4.0.2",
	"org.openrdf.sesame"     % "sesame-repository-sail"  % "2.7.12",
	"org.openrdf.sesame"     % "sesame-sail-memory"      % "2.7.12",
	"org.postgresql"         % "postgresql"              % "9.4-1201-jdbc41",
	"dk.brics.automaton"          % "automaton"   % "1.11-8", //Hermit
	"org.apache.ws.commons.axiom" % "axiom-api"   % "1.2.14", //Hermit
	"org.apache.ws.commons.axiom" % "axiom-c14n"  % "1.2.14", //Hermit
	"org.apache.ws.commons.axiom" % "axiom-dom"   % "1.2.14", //Hermit
	"org.apache.ws.commons.axiom" % "axiom-impl"  % "1.2.14"  //Hermit
)

assemblyMergeStrategy in assembly := {
	case PathList("META-INF", "axiom.xml") => MergeStrategy.first
	case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.properties") => MergeStrategy.first
	case PathList("META-INF", "maven", "com.google.guava", "guava", "pom.xml") => MergeStrategy.first
	case PathList("org", "apache", "commons", "logging", _*) => MergeStrategy.first
	//case PathList(ps @ _*) if(ps.exists(_.contains("guava")) && ps.last == "pom.xml") => {println(ps); MergeStrategy.first}
	case x =>
		val oldStrategy = (assemblyMergeStrategy in assembly).value
		oldStrategy(x)
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
import se.lu.nateko.cp.meta._
"""

Revolver.settings
