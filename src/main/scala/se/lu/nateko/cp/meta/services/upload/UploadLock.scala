package se.lu.nateko.cp.meta.services.upload

import akka.Done
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

import scala.collection.mutable.Set
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import se.lu.nateko.cp.meta.services.MetadataException

private[upload] class UploadLock {

	private val locked = Set.empty[Sha256Sum]

	def lock(hash: Sha256Sum): Try[Done] = synchronized{
		if(locked.contains(hash)) Failure(new MetadataException(s"Metadata registration for $hash is already ongoing"))
		else{
			locked.add(hash)
			Success(Done)
		}
	}

	def unlock(hash: Sha256Sum): Done = synchronized{
		locked.remove(hash)
		Done
	}
}
