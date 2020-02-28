package se.lu.nateko.cp.meta.upload.drought

import se.lu.nateko.cp.meta.services.CpVocab
import scala.io.Source
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.File
import java.io.FileReader
import scala.collection.JavaConverters.asScalaIteratorConverter
import DroughtUpload.ifNotEmpty
import se.lu.nateko.cp.meta.api.CitationClient
import se.lu.nateko.cp.meta.api.Doi
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

class AffiliationEntry(val id: Int, val name: String)

class PersonEntry(
	val id: Int, val firstName: String, val lastName: String,
	val affiliation: AffiliationEntry, urlSegm: Option[String], val orcid: Option[String]
){
	def url = "http://meta.icos-cp.eu/resources/people/" + urlSegm.getOrElse(CpVocab.getPersonCpId(firstName, lastName))
}

class FileEntry(
	val hash: Sha256Sum,
	val prevHash: Option[Sha256Sum],
	val fileName: String,
	val nPoints: Option[Int],
	val stationId: String,
	val isIcos: Boolean,
	val project: String,
	val authors: IndexedSeq[PersonEntry],
	val contribs: IndexedSeq[PersonEntry],
	val ack: Option[String],
	val papers: IndexedSeq[Doi]
){
	import DroughtMeta2.{Atmo, Fluxnet}

	def stationUrl = "http://meta.icos-cp.eu/resources/stations/" + (project match{
		case Atmo => if(isIcos) "AS" else project
		case Fluxnet => if(isIcos) "ES" else project
	}) + "_" + stationId

	def creatorUrl = project match{
		case Atmo => DroughtUpload2.atcOrg
		case Fluxnet => DroughtUpload2.etcOrg
	}

	def comment(citer: CitationClient)(implicit ctxt: ExecutionContext): Future[Option[String]] = {
		val papersComments: Future[Seq[String]] = if(papers.isEmpty)
				Future.successful(Nil)
			else
				Future.sequence(
					papers.map(doi => citer.getCitation(doi))
				).map("See also:" +: _)

		papersComments.map{papComms =>
			val all = ack.toSeq ++ papComms
			if(all.isEmpty) None else Some(all.mkString("\n"))
		}
	}
}

object DroughtMeta2{

	val Atmo = "ATMO"
	val Fluxnet = "FLUXNET"

	def parseFileEntries(
		file: File, project: String, persons: Map[Int, PersonEntry]
	): IndexedSeq[FileEntry] = parseCsv(file).map{arr =>

		def getNonEmpty(cells: Range) = cells.flatMap(i => ifNotEmpty(arr(i)))
		def getPersons(cells: Range) = getNonEmpty(cells).map(aid => persons(aid.toInt))

		new FileEntry(
			hash = Sha256Sum.unapply(arr(0).trim).get,
			prevHash = ifNotEmpty(arr(1)).flatMap(Sha256Sum.unapply),
			fileName = arr(2).trim,
			nPoints = ifNotEmpty(arr(3)).map(_.toInt),
			stationId = arr(6).trim,
			isIcos = (arr(4).trim == "-1"),
			project = project,
			authors = getPersons(13 to 16),
			contribs = getPersons(17 to 20),
			ack = ifNotEmpty(arr(21)),
			papers = getNonEmpty(22 to 23).flatMap(s => Doi.unapply(s))
		)
	}

	def parseAffiliations(file: File): Map[Int, AffiliationEntry] = parseCsv(file).iterator.map{arr =>
		val af = new AffiliationEntry(arr(0).trim.toInt, arr(1).trim)
		af.id -> af
	}.toMap

	def parsePersons(file: File, affiliations: Map[Int, AffiliationEntry]): Map[Int, PersonEntry] = parseCsv(file).iterator.map{arr =>
		val pe = new PersonEntry(
			id = arr(0).trim.toInt,
			firstName = arr(1).trim,
			lastName = arr(2).trim,
			affiliation = affiliations(arr(4).toInt),
			urlSegm = ifNotEmpty(arr(3)),
			orcid = ifNotEmpty(arr(5))
		)
		pe.id -> pe
	}.toMap

	def parseCsv[T](file: File): Vector[Array[String]] = {
		val fileReader = new FileReader(file)
		try{
			new CSVReaderBuilder(fileReader).withCSVParser(
				new CSVParserBuilder().withSeparator(',').withQuoteChar('"').build
			).build.iterator().asScala.drop(1).toVector
		}finally{
			fileReader.close()
		}
	}
}
