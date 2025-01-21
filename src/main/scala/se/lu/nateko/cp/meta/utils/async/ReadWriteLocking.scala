package se.lu.nateko.cp.meta.utils.async

import java.util.concurrent.locks.ReentrantReadWriteLock

final class ReadWriteLocking {
	private val (rl, wl) = {
		val rwl = new ReentrantReadWriteLock
		(rwl.readLock, rwl.writeLock)
	}

	def readLocked[T](reader: => T): T = {
		rl.lock()
		try{
			reader
		} finally {
			rl.unlock()
		}
	}

	def writeLocked[T](writer: => T): T = {
		wl.lock()
		try{
			writer
		} finally {
			wl.unlock()
		}
	}
}
