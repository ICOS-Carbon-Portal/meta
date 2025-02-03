package se.lu.nateko.cp.meta.persistence.postgres

import se.lu.nateko.cp.meta.api.CloseableIterator

import java.sql.{Connection, ResultSet}

class ResultSetIterator[T](connectionFactory: () => Connection, resultFactory: ResultSet => T, selectQuery: String) extends CloseableIterator[T]{
	private val conn = connectionFactory()
	conn.setAutoCommit(false)
	private val st = conn.createStatement()
	st.setFetchSize(ResultSetIterator.chunkSize)
	private val rs = st.executeQuery(selectQuery)

	private var doesHaveNext = false
	private var closed = false

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

object ResultSetIterator:
	val chunkSize = 5000
