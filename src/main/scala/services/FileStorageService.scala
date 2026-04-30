package se.lu.nateko.cp.meta.services

import scala.language.unsafeNulls

import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.utils.streams.ZipEntryFlow

import java.io.File
import java.nio.file.StandardOpenOption.*
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest

class FileStorageService(folder: File) {

	if(!folder.exists) {
		assert(folder.mkdirs(), "Failed to create directory " + folder.getAbsolutePath)
	}
	assert(folder.isDirectory, "File storage service must be initialized with a directory path")

	/**
	 * returns SHA256 hash sum of file's contents
	 */
	def saveAsFile(bs: ByteString, hashSalt: Option[Array[Byte]]): Sha256Sum = {
		val fname = getSha256(bs, hashSalt)
		val path = getPath(fname)
		
		if(!path.toFile.exists){
			try{
				val out = Files.newByteChannel(path, CREATE, WRITE)
				bs.asByteBuffers.foreach(out.write)
				out.close()
			}catch{
				case err: Throwable =>
					Files.deleteIfExists(path)
					throw err
			}
		}
		fname
	}

	def getSha256(bs: ByteString, salt: Option[Array[Byte]]): Sha256Sum = {
		val md = MessageDigest.getInstance("SHA-256")
		salt.foreach(md.update)
		bs.asByteBuffers.foreach(md.update)
		new Sha256Sum(md.digest)
	}

	def getPath(hash: Sha256Sum): Path = Paths.get(folder.getAbsolutePath, hash.hex.substring(0, 36))

	def getZipSource(fileHashesAndNames: Seq[(Sha256Sum, String)]): Source[ByteString, Any] = {

		val fileAndNamesSources = fileHashesAndNames.map{
			case (hash, name) => (name, FileIO.fromPath(getPath(hash)))
		}
		ZipEntryFlow.getMultiEntryZipStream(fileAndNamesSources)
	}
}
