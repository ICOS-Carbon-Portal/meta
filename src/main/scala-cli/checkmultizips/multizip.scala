import se.lu.nateko.cp.meta.core.crypto.Md5Sum
import se.lu.nateko.cp.meta.core.etcupload.StationId
import upickle.default.*

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import scala.collection.mutable.Buffer
import scala.util.Failure
import scala.util.Success
import scala.util.Using

given ReadWriter[Md5Sum] = readwriter[String]
	.bimap(_.hex, Md5Sum.fromHex(_).get)

given ReadWriter[Instant] = readwriter[String]
	.bimap(_.toString, Instant.parse)

final case class MultiZipEntry(
	name: String, size: Long, md5: Md5Sum, timestamp: Instant
) derives ReadWriter

/**
  * Returns information about contents of nested zip files
  *
  * @param multizip
  * @return
  */
def listEntries(multizip: File, etcFileName: String): IndexedSeq[MultiZipEntry] =
	val cachePath = os.pwd / "checkmultizips_cache" / multizip.getName

	val unfiltered = if os.exists(cachePath) then
		read[IndexedSeq[MultiZipEntry]](os.read(cachePath))
	else
		Using(FileInputStream(multizip)): fis =>
			unfoldZip(fis, close = true): (zis, entry) =>
				if entry.getName.endsWith(".zip") then
					unfoldZip(zis): (subZis, subEntry) =>
						Seq(mkMultiZipEntry(subEntry, subZis))
				else
					Seq(mkMultiZipEntry(entry, zis))
		match
			case Success(entries) => 
				os.write.over(cachePath, write(entries), createFolders = true)
				entries
			case Failure(exc) =>
				throw exc

	val levelAndFileNums = getLevelAndFileNums(etcFileName)

	unfiltered.filter: entry =>
		getLevelAndFileNums(entry.name) == levelAndFileNums

end listEntries

private def md5AndSize(is: InputStream): (Md5Sum, Long) =
	val buff = Array.ofDim[Byte](1 << 20) //1 MB buffer
	val digest = MessageDigest.getInstance("MD5")
	var size: Long = 0
	var bytesRead = is.read(buff)
	size += bytesRead
	while bytesRead != -1 do
		digest.update(buff, 0, bytesRead)
		bytesRead = is.read(buff)
		size += bytesRead
	Md5Sum(digest.digest()) -> size

private def unfoldZip[T](in: InputStream, close: Boolean = false)(
	extractor: (unzipped: InputStream, entry: ZipEntry) => Seq[T]
): IndexedSeq[T] =
	val zis = ZipInputStream(in)
	try
		Iterator.continually(zis.getNextEntry())
			.takeWhile(_ != null)
			.flatMap(extractor(zis, _))
			.toIndexedSeq
	finally if close then zis.close()

private def mkMultiZipEntry(entry: ZipEntry, is: InputStream) =
	val (md5, size) = md5AndSize(is)
	MultiZipEntry(
		name = entry.getName,
		size = size,
		timestamp = entry.getLastModifiedTime.toInstant,
		md5 = md5
	)

private def getLevelAndFileNums(fileName: String): String =
	fileName.dropRight(4).takeRight(7)