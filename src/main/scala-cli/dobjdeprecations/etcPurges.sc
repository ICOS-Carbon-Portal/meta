//> using toolkit 0.5.0

import java.time.Duration
import java.time.Instant
import sttp.model.Uri
import sttp.model.Uri.UriContext
import scala.collection.mutable

val pathDuplCsv = os.pwd / "etcDuplicates.csv"
val pathChainInfoCsv = os.pwd / "chainInfo.csv"
val pathDeprOut = os.pwd / "etcDeprecations.rq"
val pathPurgesOut = os.pwd / "etcPurges.rq"

case class DobjInfo(
	dobj: Uri, station: Uri, spec: Uri, fileName: String, size: Long,
	submTime: Instant, timeStart: Instant, timeEnd: Instant,
	old: Option[Uri]
)

def dobjInfos(): Map[Uri, DobjInfo] = os.read.lines(pathChainInfoCsv)
	.drop(1)
	.map(_.trim.split(","))
	.collect:
		case Array(station, spec, dobj, fileName, size, submTime, timeStart, timeEnd, old) =>
			val dobjUri = uri"$dobj"
			dobjUri -> DobjInfo(
				dobj = dobjUri,
				station = uri"$station",
				spec = uri"$spec",
				fileName = fileName,
				size = size.toLong,
				submTime = Instant.parse(submTime),
				timeStart = Instant.parse(timeStart),
				timeEnd = Instant.parse(timeEnd),
				old = if old.trim.isEmpty then None else Some(uri"$old")
			)
	.toMap

def nextVersLookup(infos: Map[Uri, DobjInfo]): Map[Uri, List[Uri]] =
	val m = mutable.Map.empty[Uri, List[Uri]]
	infos.values.foreach: dinfo =>
		dinfo.old.foreach: oldUri =>
			val byNow = m.getOrElse(oldUri, Nil)
			m.addOne(oldUri -> (dinfo.dobj :: byNow))
	m.toMap

def findBaddiesToPurge(): IndexedSeq[DobjInfo] =
	val infos = dobjInfos()
	val nextLookup = nextVersLookup(infos)

	def timeToDeprecation(dobj: Uri): Duration =
		val di = infos(dobj)
		val next = nextLookup.get(dobj) match
			case Some(next :: Nil) => next
			case _ => throw Exception(s"expected exactly one deprecator for $dobj")
		val depr = infos(next)
		Duration.between(di.submTime, depr.submTime)

	def isBaddie(dobj: Uri): Boolean =
		val twoMonths = Duration.ofDays(60)
		timeToDeprecation(dobj).compareTo(twoMonths) > 0 ||
			tooLargeTempCoverage(chainFrom(dobj).last)

	def chainFrom(dobj: Uri): List[Uri] =
		val nexts = nextLookup.getOrElse(dobj, Nil)
		assert(nexts.length <= 1, s"unexpected multiple deprecators for $dobj")
		dobj :: nexts.flatMap(chainFrom)

	def tooLargeTempCoverage(dobj: Uri): Boolean =
		val di = infos(dobj)
		val dur = Duration.between(di.timeStart, di.timeEnd)
		val thresh = Duration.ofDays(366)
		dur.compareTo(thresh) > 0

	nextLookup
		.collect:
			case (dobj, nexts) if nexts.size > 1 =>
				val errInfo = s"$dobj (${infos(dobj).fileName})"
				assert(nexts.size == 2, s"non-double duplicates for $errInfo")
				val baddies = nexts.filter(isBaddie)
				assert(baddies.size <= 1, s"multiple baddies for $errInfo")
				val chain = baddies.flatMap(chainFrom)
				if baddies.nonEmpty then
					println(s"baddy found: ${baddies.head} (chain of length ${chain.size}) for $errInfo")
				else println(s"no baddies amongst $nexts for $errInfo")
				chain
		.flatten
		.map(infos.apply)
		.toIndexedSeq

def writePurges(): Unit =
	val lines = findBaddiesToPurge().map: bad =>
		s"\t<${bad.dobj}>\n"
	os.write.over(pathPurgesOut, lines)
	println(s"Written to $pathPurgesOut")

writePurges()

// =================================================================================
// older experimental code
case class DuplEntry(station: Uri, spec: Uri, dobj: Uri, fileName: String, size: Long, submTime: Instant)
case class Depr(from: Uri, to: Uri)

def dupls: IndexedSeq[DuplEntry] = os.read.lines(pathDuplCsv)
	.drop(1)
	.map(_.trim.split(","))
	.collect:
		case Array(station, spec, dobj, fileName, size, submTime) =>
			DuplEntry(
				station = uri"$station",
				spec = uri"$spec",
				dobj = uri"$dobj",
				fileName = fileName,
				size = size.toLong,
				submTime = Instant.parse(submTime)
			)

def mkDepr(dupls: IndexedSeq[DuplEntry]): Depr =
	assert(dupls.size == 2, "Expected only 2 duplicates")
	val IndexedSeq(d1, d2) = dupls
	assert(d1.submTime.isBefore(d2.submTime), s"wrong ordering of the duplicates for ${d1.fileName}")
	if d1.size > d2.size then
		println(s"${d1.fileName}: ${d1.dobj} larger than ${d2.dobj}")

	Depr(from = d1.dobj, to = d2.dobj)

def deprs: IndexedSeq[Depr] = dupls.groupBy(de => de.spec -> de.station).toIndexedSeq
	.sortBy(_._2.head.station.toString)
	.map(_._2).map(mkDepr)

def writeDeprs(): Unit =
	val lines = deprs.map: depr =>
		s"\t<${depr.to}> cpmeta:isNextVersionOf <${depr.from}> .\n"
	os.write.over(pathDeprOut, lines)
	println(s"Written to $pathDeprOut")

