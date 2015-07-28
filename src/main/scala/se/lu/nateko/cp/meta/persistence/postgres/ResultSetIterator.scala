package se.lu.nateko.cp.meta.persistence.postgres

import java.sql.ResultSet
import se.lu.nateko.cp.meta.api.CloseableIterator

abstract class ResultSetIterator[T](rs: ResultSet) extends CloseableIterator[T]{

	private[this] var doesHaveNext = false
	private[this] var closed = false

	increment()

	final def hasNext: Boolean = !closed && doesHaveNext

	final def next(): T =
		if(closed){
			throw new IllegalStateException("Iterator has no more elements!")
		}else try{
			val nextItem = construct(rs)
			increment()
			nextItem
		}catch{
			case err: Throwable => close(); throw err
		}

	final def close(): Unit = if(!closed){
		closed = true
		rs.close()
		closeInternal()
	}

	protected def construct(rs: ResultSet): T
	protected def closeInternal(): Unit

	private def increment(): Unit = {
		doesHaveNext = rs.next()
		if(!doesHaveNext) close()
	}
}
