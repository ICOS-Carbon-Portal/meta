package se.lu.nateko.cp.meta.utils

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.openrdf.model.URI
import org.openrdf.model.ValueFactory
import org.openrdf.repository.Repository
import org.openrdf.repository.RepositoryConnection
import org.openrdf.repository.RepositoryResult

import se.lu.nateko.cp.meta.api.CloseableIterator

package object sesame {

	implicit class EnrichedValueFactory(val factory: ValueFactory) extends AnyVal{
		def createURI(uri: java.net.URI): URI = factory.createURI(uri.toString)
		def createLiteral(label: String, dtype: java.net.URI) = factory.createLiteral(label, createURI(dtype))
	}

	implicit class ToJavaUriConverter(val uri: URI) extends AnyVal{
		def toJava = java.net.URI.create(uri.toString)
	}

	implicit class IterableRepositoryResult[T](val res: RepositoryResult[T]) extends AnyVal{
		def asScalaIterator: CloseableIterator[T] = new RepositoryResultIterator(res, () => ())
	}

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
}