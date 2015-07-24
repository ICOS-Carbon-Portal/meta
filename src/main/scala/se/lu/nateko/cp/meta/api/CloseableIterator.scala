package se.lu.nateko.cp.meta.api


trait CloseableIterator[+T] extends Iterator[T] with java.io.Closeable