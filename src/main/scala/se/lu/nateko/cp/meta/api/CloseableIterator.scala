package se.lu.nateko.cp.meta.api


trait CloseableIterator[+T] extends Iterator[T] with java.io.Closeable{self =>

	def ++[A >: T](that: CloseableIterator[A]) = new CloseableIterator[A]{

		def hasNext = self.hasNext || that.hasNext

		def next(): A = if(self.hasNext) self.next() else that.next()

		def close(): Unit = {
			self.close()
			that.close()
		}
	}

}
