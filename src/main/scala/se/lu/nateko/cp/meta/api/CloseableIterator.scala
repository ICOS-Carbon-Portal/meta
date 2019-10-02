package se.lu.nateko.cp.meta.api


trait CloseableIterator[+T] extends Iterator[T] with java.io.Closeable{self =>

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
}
