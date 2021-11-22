package se.lu.nateko.cp.meta.api

import scala.collection.AbstractIterator


trait CloseableIterator[+T] extends Iterator[T] with AutoCloseable{self =>

	def ++[A >: T](other: => CloseableIterator[A]) = new CloseableIterator[A]{

		private[this] var thatInitialized = false
		private[this] lazy val that = {
			thatInitialized = true
			other
		}

		def hasNext = self.hasNext || that.hasNext

		def next(): A = if(self.hasNext) self.next() else that.next()

		def close(): Unit = {
			self.close()
			if(thatInitialized) that.close()
		}
	}

}

object CloseableIterator{
	def empty = new CloseableIterator[Nothing]{
		def hasNext = false
		def next(): Nothing = throw new NoSuchElementException("Empty iterator cannot have next element")
		def close(): Unit = {}
	}

	class Wrap[T](inner: Iterator[T], closer: () => Unit) extends AbstractIterator[T] with CloseableIterator[T]{

		private[this] var closed: Boolean = false

		def close(): Unit = synchronized{
			if(!closed){
				closer()
				closed = true;
			}
		}

		def hasNext: Boolean = !closed && {
			try{
				val has = inner.hasNext
				if(!has) close()
				has
			}
			catch{
				case err: Throwable =>
					close()
					throw err
			}
		}

		def next(): T =
			try{
				inner.next()
			}
			catch{
				case err: Throwable =>
					close()
					throw err
			}

	}
}
