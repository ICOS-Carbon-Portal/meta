package se.lu.nateko.cp.meta.upload.drought

import se.lu.nateko.cp.meta.services.CpVocab
import scala.io.Source
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.File
import java.io.FileReader
import java.net.URI
import scala.jdk.CollectionConverters.IteratorHasAsScala
import DroughtUpload.ifNotEmpty
import se.lu.nateko.cp.meta.services.citation.CitationClient
import se.lu.nateko.cp.meta.services.citation.Doi
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.time.LocalDate
import se.lu.nateko.cp.meta.core.data.TimeInterval
import java.time.Instant
import se.lu.nateko.cp.meta.services.citation.CitationStyle
import scala.util.Using

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
	val creationDate: LocalDate,
	val nPoints: Option[Int],
	val stationId: String,
	val stationName: String,
	val isIcos: Boolean,
	val project: String,
	val authors: IndexedSeq[PersonEntry],
	val contribs: IndexedSeq[PersonEntry],
	val ack: Option[String],
	val papers: IndexedSeq[Doi]
){
	import DroughtMeta2.{Atmo, Fluxnet}

	def stationUrl: URI = new URI(
		"http://meta.icos-cp.eu/resources/stations/" + (project match{
			case Atmo => if(isIcos) "AS" else project
			case Fluxnet => if(isIcos) "ES" else project
		}) + "_" + stationId
	)

	def creatorUrl: URI = project match{
		case Atmo => DroughtUpload2.atcOrg
		case Fluxnet => DroughtUpload2.etcOrg
	}

	def comment(citer: CitationClient)(implicit ctxt: ExecutionContext): Future[Option[String]] = {
		val papersComments: Future[Seq[String]] = if(papers.isEmpty)
				Future.successful(Nil)
			else
				Future.sequence(
					papers.map(doi => citer.getCitation(doi, CitationStyle.TEXT))
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

	val YearsRegex = """(\d{4})\-(\d{4})""".r.unanchored
	val HeightRegex = """^\w{3}_(\d+\.?\d*)m_""".r.unanchored

	def fluxFileYears(fe: FileEntry): (Int, Int) = {
		assert(fe.project == Fluxnet, s"Can parse years only from the $Fluxnet files")

		val YearsRegex(yearFromStr, yearToStr) = fe.fileName
		yearFromStr.toInt -> yearToStr.toInt
	}

	def fluxTimeInterval(fe: FileEntry): TimeInterval = {
		val (yearFrom, yearTo) = fluxFileYears(fe)
		val acqStart = Instant.parse(s"${yearFrom}-01-01T00:00:00Z")
		val acqEnd = Instant.parse(s"${yearTo + 1}-01-01T00:00:00Z")
		TimeInterval(acqStart, acqEnd)
	}

	def samplingHeightOpt(fe: FileEntry): Option[Float] = fe.fileName match{
		case HeightRegex(shStr) => Some(shStr.toFloat)
		case _ => None
	}

	def parseFileEntries(
		file: File, project: String, persons: Map[Int, PersonEntry]
	): IndexedSeq[FileEntry] = parseCsv(file).map{arr =>

		def getNonEmpty(cells: Range) = cells.flatMap(i => ifNotEmpty(arr(i)))
		def getPersons(cells: Range) = getNonEmpty(cells).map(aid => persons(aid.toInt))

		new FileEntry(
			hash = Sha256Sum.unapply(arr(0).trim).get,
			prevHash = ifNotEmpty(arr(1)).flatMap(Sha256Sum.unapply),
			fileName = arr(2).trim,
			creationDate = LocalDate.parse(arr(3).trim),
			nPoints = ifNotEmpty(arr(4)).map(_.toInt),
			stationId = arr(7).trim,
			stationName = arr(6).trim,
			isIcos = (arr(5).trim == "-1"),
			project = project,
			authors = getPersons(14 to 17),
			contribs = getPersons(18 to 21),
			ack = ifNotEmpty(arr(22)),
			papers = getNonEmpty(23 to 24).flatMap(s => Doi.unapply(s))
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

	private def parseCsv[T](file: File): Vector[Array[String]] =
		Using(new FileReader(file)){reader =>
			new CSVReaderBuilder(reader).withCSVParser(
				new CSVParserBuilder().withSeparator(',').withQuoteChar('"').build
			).build.iterator().asScala.drop(1).toVector
		}.get
}
