package se.lu.nateko.cp.meta.services.upload

import akka.Done
import scala.collection.mutable.Set
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.services.MetadataException

private[upload] class UploadLock {

	private val locked = Set.empty[Sha256Sum]

	def lock(hash: Sha256Sum): Try[Done] = synchronized{
		if(locked.contains(hash)) Failure(new MetadataException(s"Metadata registration for ${hash.id} is already ongoing"))
		else{
			locked.add(hash)
			Success(Done)
		}
	}

	def unlock(hash: Sha256Sum): Done = synchronized{
		locked.remove(hash)
		Done
	}

	def wrapTry[T](hash: Sha256Sum)(inner: => Try[T]): Try[T] =
		val lockTry = lock(hash)
		val res = lockTry.flatMap(_ => inner)
		if lockTry.isSuccess then unlock(hash)
		res

	def wrapFuture[T](hash: Sha256Sum)(inner: => Future[T])(using ExecutionContext): Future[T] =
		val lockTry = lock(hash)
		val res = Future.fromTry(lockTry).flatMap(_ => inner)
		if lockTry.isSuccess then
			res.andThen:
				case _ => unlock(hash)
		else res

}
