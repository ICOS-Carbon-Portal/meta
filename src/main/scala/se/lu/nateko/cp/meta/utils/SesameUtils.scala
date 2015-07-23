package se.lu.nateko.cp.meta.utils

import org.openrdf.repository.Repository
import org.openrdf.repository.RepositoryConnection
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import org.openrdf.repository.RepositoryResult
import scala.collection.AbstractIterator

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

//		def access[T](accessor: RepositoryConnection => T): Try[T] = {
//			val conn = repo.getConnection
//			try{
//				Success(accessor(conn))
//			}
//			catch{
//				case err: Throwable => Failure(err)
//			}
//			finally{
//				conn.close()
//			}
//		}

		//TODO Handle the case of incomplete consumption of the returned iterator
		def access[T](accessor: RepositoryConnection => RepositoryResult[T]): Iterator[T] = {
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
		def asScalaIterator: Iterator[T] = new RepositoryResultIterator(res, () => ())
	}

	private class RepositoryResultIterator[T](res: RepositoryResult[T], closer: () => Unit) extends AbstractIterator[T]{

		private[this] var closed: Boolean = false

		private def close(): Unit = {
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
