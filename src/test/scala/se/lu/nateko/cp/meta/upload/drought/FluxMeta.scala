package se.lu.nateko.cp.meta.upload.drought
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.URI
import se.lu.nateko.cp.meta.core.etcupload.StationId
import java.time.Instant
import se.lu.nateko.cp.meta.upload.CpUploadClient
import java.util.zip.ZipFile
import java.nio.file.Path
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter


class FluxMeta(
	val hash: Sha256Sum, val filePath: Path, val nPoints: Option[Int], val isIcos: Boolean,
	val pi: URI, val ack: Option[String], val papers: Seq[String]
){

	val StationId(stationId) = fname.substring(4, 10)

	def station: URI = {
		val pref = if(isIcos) "ES_" else "FLUXNET_"
		new URI(s"http://meta.icos-cp.eu/resources/stations/$pref${stationId.id}")
	}

	def yearFrom = fname.substring(31, 35).toInt
	def yearTo = fname.substring(36, 40).toInt

	def acqStart = Instant.parse(
		s"${yearFrom}-01-01T00:00:00Z"
	)

	def acqEnd = Instant.parse(
		s"${yearTo + 1}-01-01T00:00:00Z"
	)

	def comment: Option[String] = if(ack.isEmpty && papers.isEmpty) None else Some{
		val ref: Option[String] = if(papers.isEmpty) None else Some("See also:")
		(ack.toSeq ++ ref.toSeq ++ papers).mkString("\n")
	}

	def fileInfo = new CpUploadClient.FileInfo(filePath, hash)

	def fname: String = filePath.getFileName.toString

	def creationDate = Instant.ofEpochMilli(
		new ZipFile(filePath.toFile).entries().asScala.map(_.getTime).max
	)

}
