package se.lu.nateko.cp.meta.services.upload

import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.test.services.upload.UploadTestFixture.{hash, other}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class UploadLockTests extends AsyncFunSpec{
	it("rejects overlapping synchronous registrations per hash and releases the lock however they end"){
		val lock = new UploadLock
		assert(lock.lock(hash).isSuccess)
		var evaluated = false
		val rejected = lock.wrapTry(hash) { evaluated = true; Success(1) }
		assert(rejected.failed.get.isInstanceOf[MetadataException])
		assert(!evaluated)
		assert(lock.wrapTry(other)(Success(2)).get == 2)
		assert(lock.lock(hash).isFailure) // Rejected operations must not release the active lock.
		lock.unlock(hash)
		val error = new RuntimeException("registration failed")
		for (result <- Seq(Success(1), Failure(error))) {
			assert(lock.wrapTry(hash)(result) == result)
			assert(lock.wrapTry(hash)(Success(2)).get == 2)
		}
		assert(lock.wrapTry[Int](hash)(throw error).failed.get eq error)
		assert(lock.wrapTry(hash)(Success(3)).get == 3)
	}

	it("keeps asynchronous locks until completion and releases them on success or failure"){
		val lock = new UploadLock
		def check(fail: Boolean): Future[org.scalatest.Assertion] = {
			val pending = Promise[Int]()
			val started = Promise[Unit]()
			val operation = lock.wrapFuture(hash) { started.success(()); pending.future }
			started.future.flatMap{ _ =>
				var evaluated = false
				val rejected = lock.wrapFuture(hash) { evaluated = true; Future.successful(99) }
				rejected.failed.flatMap{ rejection =>
					assert(rejection.isInstanceOf[MetadataException])
					assert(!evaluated)
					assert(lock.lock(hash).isFailure)
					if fail then pending.failure(new RuntimeException("completion failed")) else pending.success(1)
					operation.transform{ result =>
						assert(result.isFailure == fail)
						Success(assert(lock.wrapTry(hash)(Success(3)).get == 3))
					}
				}
			}
		}
		check(false).flatMap(_ => check(true))
	}
}
