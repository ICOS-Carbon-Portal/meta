package se.lu.nateko.cp.meta.utils.async

import java.util.concurrent.locks.ReentrantReadWriteLock

trait ReadWriteLocking {

	private val (rl, wl) = {
		val rwl = new ReentrantReadWriteLock
		(rwl.readLock, rwl.writeLock)
	}

	protected def readLocked[T](reader: => T): T = {
		rl.lock()
		try{
			reader
		} finally {
			rl.unlock()
		}
	}

	protected def writeLocked[T](writer: => T): T = {
		wl.lock()
		try{
			writer
		} finally {
			wl.unlock()
		}
	}
}
