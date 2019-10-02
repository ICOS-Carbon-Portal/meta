package se.lu.nateko.cp.meta.api


trait CloseableIterator[+T] extends Iterator[T] with java.io.Closeable{self =>

	def ++[A >: T](that: => CloseableIterator[A]) = new CloseableIterator[A]{

		private[this] var thatInitialized = false

		def hasNext = self.hasNext || { thatInitialized = true; that.hasNext }

		def next(): A = if(self.hasNext) self.next() else { thatInitialized = true; that.next() }

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
}
