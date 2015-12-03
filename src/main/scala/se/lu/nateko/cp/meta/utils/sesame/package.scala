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
import org.openrdf.model.Value
import org.openrdf.model.Statement
import org.openrdf.model.Literal
import org.openrdf.model.vocabulary.XMLSchema

package object sesame {

	implicit class EnrichedValueFactory(val factory: ValueFactory) extends AnyVal{
		def createURI(uri: java.net.URI): URI = factory.createURI(uri.toString)
		def createURI(base: java.net.URI, fragment: String): URI = factory.createURI(base.toString, fragment)
		def createURI(base: URI, fragment: String): URI = factory.createURI(base.stringValue, fragment)
		def createLiteral(label: String, dtype: java.net.URI) = factory.createLiteral(label, createURI(dtype))
		
		def tripleToStatement(triple: (URI, URI, Value)): Statement =
			factory.createStatement(triple._1, triple._2, triple._3)

		def getDateTimeNow: Literal =
			factory.createLiteral(DateTimeUtils.defaultNowString, XMLSchema.DATETIME)
	}

	implicit def javaUriToSesame(uri: java.net.URI)(implicit factory: ValueFactory): URI = factory.createURI(uri)
	implicit def sesameUriToJava(uri: URI) = java.net.URI.create(uri.stringValue)

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

		def access[T](accessor: RepositoryConnection => RepositoryResult[T]): CloseableIterator[T] = access(accessor, () => ())

		def access[T](accessor: RepositoryConnection => RepositoryResult[T], extraCleanup: () => Unit): CloseableIterator[T] = {
			val conn = repo.getConnection

			def finalCleanup(): Unit = {
				conn.close()
				extraCleanup()
			}

			try{
				val repRes = accessor(conn)
				new RepositoryResultIterator(repRes, finalCleanup)
			}
			catch{
				case err: Throwable =>
					finalCleanup()
					throw err
			}
		}

		def accessEagerly[T](accessor: RepositoryConnection => T): T = {
			val conn = repo.getConnection
			try{
				accessor(conn)
			}
			finally{
				conn.close()
			}
		}
	}
}