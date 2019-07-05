package se.lu.nateko.cp.meta

import scala.concurrent.Future
import akka.http.scaladsl.model.Uri
import java.nio.file.Path
import java.nio.file.Paths
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import com.opencsv.CSVReader
import java.io.FileReader
import java.net.URI
import java.time.Instant
import scala.collection.JavaConverters.asScalaIteratorConverter
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.LocalDateTime

object UploadWorkbench{

}

object Draught2018{
	val baseDir = Paths.get("/home/oleg/workspace/cpupload/draught2018")
	val metaSrcPath = baseDir.resolve(Paths.get("fluxnetMetaSrc.csv"))
	def csv = new CSVReader(new FileReader(metaSrcPath.toFile))

	def metas: Iterator[FluxMeta] = csv.iterator().asScala.drop(1).map(parseFluxMeta)

	def makeDto(meta: FluxMeta): ObjectUploadDto = {
		???
	}

	def parseFluxMeta(row: Array[String]) = new FluxMeta(
		hash = Sha256Sum.fromHex(row(0)).get,
		fname = row(1),
		isIcos = row(2) == "yes",
		pi = new URI("http://meta.icos-cp.eu/resources/people/" + row(3)),
		ack = ifNotEmpty(row(4)),
		papers = ifNotEmpty(row(5)).toSeq ++ ifNotEmpty(row(6))
	)

	private def ifNotEmpty(s: String): Option[String] = Option(s).map(_.trim).filter(_.length > 0)

	class FluxMeta(val hash: Sha256Sum, val fname: String, val isIcos: Boolean, val pi: URI, val ack: Option[String], val papers: Seq[String]){

		def station: URI = {
			val statId = fname.substring(4, 10)
			val pref = if(isIcos) "ES_" else "FLUXNET_"
			new URI(s"http://meta.icos-cp.eu/resources/stations/$pref$statId")
		}

		def ackStart = Instant.parse(
			s"${fname.substring(31, 35)}-01-01T00:00:00Z"
		)

		def ackEnd = Instant.parse(
			s"${fname.substring(37, 41).toInt + 1}-01-01T00:00:00Z"
		)

		def comment: Option[String] = if(ack.isEmpty && papers.isEmpty) None else Some{
			val ref: Option[String] = if(papers.isEmpty) None else Some("See also:")
			(ack.toSeq ++ ref.toSeq ++ papers).mkString("\n")
		}

		def creationDate: Instant = {
			import scala.sys.process.Process
			val zipInfo = Process(Seq("unzip", "-Z", "-T", fname), baseDir.toFile).!!
			val dtStr = zipInfo.split('\n')(2).split("\\s+")(6)
			val localDt = LocalDateTime.parse(dtStr, DateTimeFormatter.ofPattern("uuuuMMdd.HHmmss"))
			ZonedDateTime.of(localDt, ZoneId.systemDefault).toInstant
		}
	}
}
