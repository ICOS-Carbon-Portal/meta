//> using toolkit default
//> using repository "https://repo.icos-cp.eu/content/groups/public"
//> using dep se.lu.nateko.cp::meta-core:0.7.18
//> using file sparql.scala
//> using file multizip.scala
import se.lu.nateko.cp.meta.core.crypto.Md5Sum

import java.time.Instant
import sttp.client4.quick.UriContext
import sttp.model.Uri


val potentiallyProblematicResubmissionsQuery = """
	prefix xsd: <http://www.w3.org/2001/XMLSchema#>
	prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	prefix prov: <http://www.w3.org/ns/prov#>
	select distinct ?fileName ?oldObj ?newerDobj ?oldSubmTime ?newerSubmTime ?spec ?oldSize ?size
	where {
		{
			select * where{
				VALUES ?spec {<http://meta.icos-cp.eu/resources/cpmeta/etcEddyFluxRawSeriesCsv> <http://meta.icos-cp.eu/resources/cpmeta/etcEddyFluxRawSeriesBin>}
				?oldObj cpmeta:hasObjectSpec ?spec .
				?oldObj cpmeta:hasSizeInBytes ?oldSize .
				?oldObj cpmeta:hasName ?fileName .
				?oldObj cpmeta:wasSubmittedBy/prov:endedAtTime ?oldSubmTime .
				FILTER EXISTS {[] cpmeta:isNextVersionOf ?oldObj}
			}
		}
		?newerDobj cpmeta:isNextVersionOf ?oldObj .
		?newerDobj cpmeta:wasSubmittedBy/prov:endedAtTime ?newerSubmTime .
		#filter(?newerSubmTime > "2022-10-12T12:00:00Z"^^xsd:dateTime && ?newerSubmTime < "2023-03-07T15:00:00Z"^^xsd:dateTime)
		?newerDobj cpmeta:hasSizeInBytes ?size .
	}
	order by ?fileName ?newerSubmTime
"""

case class Submission(uri: Uri, time: Instant, size: Long)
case class Suspicious(fileName: String, old: Submission, newer: Submission)

def suspiciousList: Seq[Suspicious] = sparqlSelect(
		uri"https://meta.icos-cp.eu/sparql",
		potentiallyProblematicResubmissionsQuery
	).map: row =>
		Suspicious(
			fileName = row.fileName,
			old = Submission(
				uri = Uri.unsafeParse(row.oldObj),
				time = Instant.parse(row.oldSubmTime),
				size = row.oldSize.toLong
			),
			newer = Submission(
				uri = Uri.unsafeParse(row.newerDobj),
				time = Instant.parse(row.newerSubmTime),
				size = row.size.toLong
			)
		)

def submissionsByFilename: Seq[(String, IndexedSeq[Submission])] = suspiciousList
	.groupBy(_.fileName)
	.mapValues(_.flatMap(s => Seq(s.old, s.newer)).distinctBy(_.uri).sortBy(_.time).toIndexedSeq)
	.toSeq
	.sortBy(_._1)

type SubEntry = (String, Md5Sum)

def report(fname: String, subms: IndexedSeq[Submission]): Unit =

	val submsAndEntries: IndexedSeq[(Submission, Map[String, MultiZipEntry])] = subms.map: subm =>
		val fileName = subm.uri.path.last
		//val file = os.root / "media" / "Mechanical" / "data" / "multizips" / fileName
		val file = os.root / "disk" / "data" / "dataAppStorage" / "etcRawTimeSerMultiZip" / fileName
		val entries = try listEntries(file.toIO, fname).map(e => e.name -> e).toMap
			catch case exc: Throwable =>
				//println(s"WARNING $fname (${subm.uri}) could not read ZIP entries: ${exc.getMessage}")
				Map.empty
		//println(s"\tFound ${entries.size} entries in version ${subm.uri}")
		// if entries.size > 48 then
		// 	println(s"WARNING $fname version ${subm.uri} has ${entries.size} subfiles")
		subm -> entries


	val latestSubfiles: IndexedSeq[SubEntry] = submsAndEntries
		.iterator
		.flatMap(_._2.valuesIterator).toSeq
		.groupBy(_.name)
		.mapValues(_.maxBy(_.timestamp).md5)
		.toIndexedSeq
		.sortBy(_._1)

	val stats = for (subm, entries) <- submsAndEntries yield
		val missing: Seq[SubEntry] = latestSubfiles.filterNot: (fname, md5) =>
			entries.get(fname).exists(_.md5 == md5)

		(subm, entries, missing)

	val (_, lastEntries, lastMissing) = stats.last
	val oldCompleteExists = stats.dropRight(1).exists(_._3.isEmpty)

	if lastMissing.nonEmpty || oldCompleteExists then
		println(s"\nReporting on ${subms.length} versions of package $fname")
		for (subm, entries, missing) <- stats do
			val status =
				if missing.isEmpty then s"is OK (${entries.size} subfiles)"
				else s"has ${entries.size} subfiles but is missing ${missing.size} of the ${latestSubfiles.size} latest subfiles"
			println(s"\tVersion ${subm.uri} $status")

		if oldCompleteExists then
			stats.iterator.dropWhile(_._3.nonEmpty).drop(1).foreach: (subm, _, _) =>
				println(s"\tSolution: purge ${subm.uri}")
		else
			//if lastMissing.size < 15 then
			stats.foreach: (subm, entries, _) =>
				if lastMissing.forall: (filename, md5) =>
					entries.get(filename).exists(_.md5 == md5)
				then
					println(s"\tSolution: version ${subm.uri} contains files missing in the latest version:")
					lastMissing.foreach: (filename, md5) =>
						println(s"\t\t$filename (innermost file content MD5 $md5)")
	// else
	// 	println(s"OK $fname, ${subms.length} versions, ${lastEntries.size} subfiles in the latest")

end report

val allResubmitted = submissionsByFilename
println(s"Total number of multiple-version daily packages: ${allResubmitted.size}")
for (fileName, subms) <- allResubmitted do
	//if fileName.startsWith("BE-Lon") || fileName.startsWith("CH-Dav") then// fileName > "BE-Maa_EC_20220726_L01_F01.zip" then
	try report(fileName, subms)
	catch case exc: Throwable =>
		println(s"ERROR $fileName, processing error: ${exc.getMessage}")
		//exc.printStackTrace()


// val testFileName = "FR-Lus_EC_20230217_L01_F01.zip"
// report(testFileName, submissionsByFilename(testFileName))

// submissionsByFilename.foreach: (fname, subms) =>
// 	println("\t" + fname)
// 	subms foreach println
//val testfile = File("/media/Mechanical/data/multizips/ETBCzNdgrllJwiwPrrQAy6qa")
