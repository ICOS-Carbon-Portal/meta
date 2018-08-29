package se.lu.nateko.cp.meta.utils.rdf4j

import scala.collection.AbstractIterator

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import se.lu.nateko.cp.meta.api.CloseableIterator


//TODO Make this thread-safe ?
class Rdf4jIterationIterator[T](res: CloseableIteration[T, _], closer: () => Unit = () => ()) extends AbstractIterator[T] with CloseableIterator[T]{

	private[this] var closed: Boolean = false

	def close(): Unit = if(!closed){
		closed = true;
		try{
			res.close()
		} finally {
			closer()
		}
	}

	def hasNext: Boolean = !closed && {
		try{
			val has = res.hasNext()
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
			res.next()
		}
		catch{
			case err: Throwable =>
				close()
				throw err
		}

	override protected def finalize(): Unit = {
		close()
		super.finalize()
	}
}
