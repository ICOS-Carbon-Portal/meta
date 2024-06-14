//> using toolkit "0.4.0"
import java.time.ZoneId
import java.time.LocalDate
import java.time.ZonedDateTime

import java.time.Instant
import java.time.format.DateTimeFormatter

// the log files were obtained with command:
// git log --pretty=format:"%h | %an | %ad | %s" > repo_name_git_log.txt
val tsFormat = DateTimeFormatter.ofPattern("EEE LLL d HH:mm:ss yyyy Z")

case class LogEntry(author: String, ts: Instant)

case class RepoStats(
	repo: String,
	nCommits: Int,
	firstCommit: LocalDate,
	lastCommit: LocalDate
):
	override def toString() = productIterator.mkString(",")

case class AuthorStats(
	author: String,
	nCommits: Int,
	firstCommit: LocalDate,
	lastCommit: LocalDate
):
	override def toString() = productIterator.mkString(",")

extension(ts: Instant)
	def getDate: LocalDate = ts.atZone(ZoneId.systemDefault()).toLocalDate

val authorsMap: Map[String, String] = Map(
	//Anders Dahlner
	"andre" -> "André Bjärby",
	"andreby" -> "André Bjärby",
	"cd" -> "Claudio D'Onofrio",
	"claudio" -> "Claudio D'Onofrio",
	"claudiodonofrio" -> "Claudio D'Onofrio",
	"Moritz Makowski (he/him)" -> "Moritz Makowski",
	//Jonathan Thiry
	"Jonathan-Schenk" -> "Jonathan Schenk",
	"KarolinaPntzt" -> "Karolina Pantazatou",
	"Klara" -> "Klara Broman",
	"klarakristina" -> "Klara Broman",
	//Mitch Selander
	//Oleg Mirzov
	"Paul" -> "Paul Hedberg",
	"paul" -> "Paul Hedberg",
	"roger-groth" -> "Roger Groth",
	//Steve Jones
	//Tyler Erickson
	"ukarst" -> "Ute Karstens",
	"ZogopZ" -> "Zois Zogopoulos",
	"zois" -> "Zois Zogopoulos",
	"zoiszogop@gmail.com" -> "Zois Zogopoulos",
	"aadamaki" -> "Angeliki Adamaki",
	//dependabot[bot]
	"idastorm" -> "Ida Storm",
	"lillyanderssonn" -> "Lilly Andersson",
	"No contribution" -> "no contribution",
)

def parseEntry(line: String) =
	val parts = line.split("\\|").map(_.trim)
	LogEntry(
		author = authorsMap.getOrElse(parts(1), parts(1)),
		ts = ZonedDateTime.parse(parts(2), tsFormat).toInstant
	)

def logFiles = os.list(os.pwd / "logs")

def repoStats: Seq[RepoStats] = logFiles.map: log =>
	val lines = os.read.lines(log)
	RepoStats(
		repo = log.last.split("_").head,
		nCommits = lines.length,
		firstCommit = parseEntry(lines.last).ts.getDate,
		lastCommit = parseEntry(lines.head).ts.getDate
	)

//repoStats.sortBy(_.firstCommit).foreach(println)
//println()

def allEntries: Iterator[LogEntry] = logFiles.iterator.flatMap(os.read.lines(_)).map(parseEntry)
def allAuthors: Seq[String] = allEntries.map(_.author).distinct.toSeq.sorted

def parseAuthorStats(author: String, entries: Seq[LogEntry]) = AuthorStats(
	author = author,
	nCommits = entries.length,
	firstCommit = entries.map(_.ts).min.getDate,
	lastCommit = entries.map(_.ts).max.getDate
)

def authorStats = allEntries.toSeq.groupBy(_.author).iterator.map(parseAuthorStats).toSeq

//authorStats.sortBy(_.nCommits).foreach(println)

val NumStatsRegex = """^(\d+)\s+(\d+)\s+.+\.(\w+)$""".r

case class NumStats(linesAdded: Int, linesGrown: Int):
	def +(other: NumStats): NumStats =
		NumStats(linesAdded + other.linesAdded, linesGrown + other.linesGrown)


val blackListedExts = Set("rdf", "html0", "example", "svg", "json", "lock", "bak", "typed", "npmrc", "swcrc", "CO2", "scss")

def listNumStats(path: os.Path): Map[String, NumStats] =
	os.read.lines(path)
		.collect:
			case NumStatsRegex(addStr, remStr, ext) if !blackListedExts.contains(ext) =>
				ext -> NumStats(addStr.toInt, addStr.toInt - remStr.toInt)
		.groupMapReduce(_._1)(_._2)(_ + _)

val numStats = listNumStats(os.pwd / "numstats" / "data_numstats.txt")

numStats.toSeq.sortBy((ext, stat) => -stat.linesAdded).foreach(println)