package se.lu.nateko.cp.meta.utils.sesame

import scala.collection.AbstractIterator

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import se.lu.nateko.cp.meta.api.CloseableIterator


//TODO Make this thread-safe ?
class SesameIterationIterator[T](res: CloseableIteration[T, _], closer: () => Unit = () => ()) extends AbstractIterator[T] with CloseableIterator[T]{

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
