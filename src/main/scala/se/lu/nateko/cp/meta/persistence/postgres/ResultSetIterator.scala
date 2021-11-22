package se.lu.nateko.cp.meta.persistence.postgres

import java.sql.ResultSet
import se.lu.nateko.cp.meta.api.CloseableIterator
import java.sql.Connection

class ResultSetIterator[T](connectionFactory: () => Connection, resultFactory: ResultSet => T, selectQuery: String) extends CloseableIterator[T]{
	private[this] val conn = connectionFactory()
	private[this] val st = conn.createStatement()
	private[this] val rs = st.executeQuery(selectQuery)

	private[this] var doesHaveNext = false
	private[this] var closed = false

	increment()

	final def hasNext: Boolean = !closed && doesHaveNext

	final def next(): T =
		if(closed || !doesHaveNext){
			throw new IllegalStateException("Iterator has no more elements!")
		}else try{
			val nextItem = resultFactory(rs)
			increment()
			nextItem
		}catch{
			case err: Throwable => close(); throw err
		}

	final def close(): Unit = if(!closed){
		try{
			rs.close()
			st.close()
		}finally{
			conn.close()
			closed = true
		}
	}

	private def increment(): Unit = {
		doesHaveNext = rs.next()
		if(!doesHaveNext) close()
	}
}
