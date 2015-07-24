package se.lu.nateko.cp.meta.utils

import scala.collection.AbstractIterator
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.openrdf.repository.Repository
import org.openrdf.repository.RepositoryConnection
import org.openrdf.repository.RepositoryResult

import se.lu.nateko.cp.meta.api.CloseableIterator

object SesameUtils{

	implicit class SesameRepoWithAccessAndTransactions(val repo: Repository) extends AnyVal{

		def transact(action: RepositoryConnection => Unit): Try[Unit] = {
			val conn = repo.getConnection
			conn.begin()
			
			try{
				action(conn)
				Success(conn.commit())
			}
			catch{
				case err: Throwable =>
					conn.rollback()
					Failure(err)
			}
			finally{
				conn.close()
			}
		}

		def access[T](accessor: RepositoryConnection => RepositoryResult[T]): CloseableIterator[T] = {
			val conn = repo.getConnection
			try{
				val repRes = accessor(conn)
				new RepositoryResultIterator(repRes, conn.close)
			}
			catch{
				case err: Throwable =>
					conn.close()
					throw err
			}
		}
	}

	implicit class IterableRepositoryResult[T](val res: RepositoryResult[T]) extends AnyVal{
		def asScalaIterator: CloseableIterator[T] = new RepositoryResultIterator(res, () => ())
	}

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
}
