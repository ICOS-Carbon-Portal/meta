package se.lu.nateko.cp.meta.utils.sesame

import org.openrdf.repository.RepositoryResult
import scala.collection.AbstractIterator
import se.lu.nateko.cp.meta.api.CloseableIterator


//TODO Make this thread-safe
private class RepositoryResultIterator[T](res: RepositoryResult[T], closer: () => Unit) extends AbstractIterator[T] with CloseableIterator[T]{

	private[this] var closed: Boolean = false

	def close(): Unit = {
		closed = true;
		res.close()
		closer()
	}

	def hasNext: Boolean = !closed && {
		val has = res.hasNext()
		if(!has) close()
		has
	}

	def next(): T =
		try{
			res.next()
		}
		catch{
			case err: Throwable =>
				close()
				throw err
		}
}
