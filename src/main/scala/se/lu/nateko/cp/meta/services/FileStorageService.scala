package se.lu.nateko.cp.meta.services

import java.io.File
import akka.util.ByteString
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption._

class FileStorageService(folder: File) {

	if(!folder.exists) {
		assert(folder.mkdirs(), "Failed to create directory " + folder.getAbsolutePath)
	}
	assert(folder.isDirectory, "File storage service must be initialized with a directory path")

	/**
	 * returns SHA256 hash sum of file's contents
	 */
	def saveAsFile(bs: ByteString, hashSalt: Option[Array[Byte]]): String = {
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

	def getSha256(bs: ByteString, salt: Option[Array[Byte]]): String = {
		val md = MessageDigest.getInstance("SHA-256")
		salt.foreach(md.update)
		bs.asByteBuffers.foreach(md.update)
		md.digest.map("%02x" format _).mkString
	}

	def getPath(hash: String) = Paths.get(folder.getAbsolutePath, hash)
}