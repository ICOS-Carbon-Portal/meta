package se.lu.nateko.cp.meta.services.labeling

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import scala.concurrent.Future

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString

object ZipEntryStreaming {

	type FileLikeSource = Source[ByteString, Future[Any]]

	def getMultiEntryZipStream(
		entries: Seq[(String, FileLikeSource)]
	) (implicit mat: Materializer): Source[ByteString, Future[Done]] = {

		StreamConverters.asOutputStream().mapMaterializedValue { os =>
			val zos = new ZipOutputStream(os)
			implicit val ctxt = mat.executionContext

			def writeEntries(entries: Seq[(String, FileLikeSource)]): Future[Done] = entries match {
				case Seq() => Future{
					zos.close()
					Done
				}
				case Seq((fileName, source), rest @ _*) =>
					zos.putNextEntry(new ZipEntry(fileName))
					source.runForeach{ bs =>
						val arr = Array.ofDim[Byte](bs.size)
						bs.copyToArray(arr)
						zos.write(arr)
					}.flatMap{_ =>
						zos.closeEntry()
						writeEntries(rest)
					}
			}
			writeEntries(entries)
		}
	}

}