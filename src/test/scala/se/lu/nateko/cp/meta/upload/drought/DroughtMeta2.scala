package se.lu.nateko.cp.meta.upload.drought

import com.opencsv.{CSVParserBuilder, CSVReaderBuilder}
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.citation.{CitationClient, CitationStyle}

import java.io.{File, FileReader}
import java.net.URI
import java.time.{Instant, LocalDate}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.Using

import DroughtUpload.ifNotEmpty

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
	val project: DroughtMeta2.Project,
	val authors: IndexedSeq[PersonEntry],
	val contribs: IndexedSeq[PersonEntry],
	val ack: Option[String],
	val papers: IndexedSeq[Doi]
){
	import DroughtMeta2.{Atmo}

	def stationUrl: URI = new URI(
		"http://meta.icos-cp.eu/resources/stations/" + (project match{
			case Atmo => if(isIcos) "AS" else "ATMO"
			case _ => if(isIcos) "ES" else "FLUXNET"
		}) + "_" + stationId
	)

	def creatorUrl: URI = project match{
		case Atmo => FluxdataUpload.atcOrg
		case _ => FluxdataUpload.etcOrg
	}

	def comment(citer: CitationClient)(implicit ctxt: ExecutionContext): Future[Option[String]] = {
		val papersComments: Future[Seq[String]] = if(papers.isEmpty)
				Future.successful(Nil)
			else
				Future.sequence(
					papers.map(
						doi => citer.getCitation(doi, CitationStyle.HTML).recover{
							case _: Throwable => s"https://doi.org/${doi.prefix}/${doi.suffix}"
						}
					)
				).map("See also:" +: _)

		papersComments.map{papComms =>
			val all = ack.toSeq ++ papComms
			if(all.isEmpty) None else Some(all.mkString("\n"))
		}
	}
}

object DroughtMeta2{

	sealed trait Project
	case object Atmo extends Project
	case object Winter2020 extends Project
	case object Winter2020Hh extends Project

	val YearsRegex = """(\d{4})\-(\d{4})""".r.unanchored
	val HeightRegex = """^\w{3}_(\d+\.?\d*)m_""".r.unanchored

	def fluxFileYears(fe: FileEntry): (Int, Int) =
		assert(fe.project != Atmo, s"Can parse years only from Fluxnet files")

		fe.fileName match
			case YearsRegex(yearFromStr, yearToStr) => yearFromStr.toInt -> yearToStr.toInt
			case _ => throw new Exception(s"Bad filename ${fe.fileName}")


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
		file: File, project: Project, persons: Map[Int, PersonEntry]
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
			papers = getNonEmpty(23 to 24).flatMap(Doi.parse(_).toOption)
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
