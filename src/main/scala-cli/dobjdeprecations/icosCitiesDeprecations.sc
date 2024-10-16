//> using toolkit 0.5.0
//> using repository "https://repo.icos-cp.eu/content/groups/public"
//> using dep "se.lu.nateko.cp::meta-core:0.7.20"

import sttp.model.Uri
import sttp.model.Uri.UriContext
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

case class Depr(from: Uri, to: Uri)

val pathIn = os.root / "home" / "oleg" / "Downloads" / "deprecate(1).csv"
val pathOut = pathIn / os.RelPath("../deprecate.rq")
val baseUri = uri"https://citymeta.icos-cp.eu/objects/"

val deprs: IndexedSeq[Depr] = os.read.lines(pathIn)
	.drop(1)
	.map(_.trim.split(";"))
	.collect:
		case Array(_, newHashStr, oldHashStr) if !oldHashStr.isEmpty =>
			val newHash = Sha256Sum.fromString(newHashStr).get
			val oldHash = Sha256Sum.fromString(oldHashStr).get
			Depr(from = uri"$baseUri${oldHash.id}", to =  uri"$baseUri${newHash.id}")

val multiDeprecated: Map[Uri, Int] = deprs
	.groupMapReduce(_.from)(_ => 1)(_ + _)
	.filter((from, count) => count > 1)

multiDeprecated.toSeq.sortBy(- _._2).foreach:
	case (from, count) => println(s"$from : $count times")

if multiDeprecated.isEmpty then
	println("No multiple deprecations detected")
	writeDeprs

def writeDeprs: Unit =
	val lines = deprs.map: depr =>
		s"\t<${depr.to}> cpmeta:isNextVersionOf <${depr.from}> .\n"
	os.write.over(pathOut, lines)
	println(s"Written to $pathOut")