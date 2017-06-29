package se.lu.nateko.cp.meta.utils

import java.net.{URI => JavaUri}
import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.repository.RepositoryResult
import se.lu.nateko.cp.meta.api.CloseableIterator
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.vocabulary.XMLSchema

package object sesame {

	implicit class EnrichedValueFactory(val factory: ValueFactory) extends AnyVal{
		def createIRI(uri: JavaUri): IRI = factory.createIRI(uri.toString)
		def createIRI(base: JavaUri, fragment: String): IRI = factory.createIRI(base.toString, fragment)
		def createIRI(base: IRI, fragment: String): IRI = factory.createIRI(base.stringValue, fragment)
		def createLiteral(label: String, dtype: JavaUri) = factory.createLiteral(label, createIRI(dtype))
		def createStringLiteral(label: String) = factory.createLiteral(label, XMLSchema.STRING)

		def tripleToStatement(triple: (IRI, IRI, Value)): Statement =
			factory.createStatement(triple._1, triple._2, triple._3)
	}

	implicit def javaUriToSesame(uri: JavaUri)(implicit factory: ValueFactory): IRI = factory.createIRI(uri)
	implicit def sesameUriToJava(uri: IRI): JavaUri = JavaUri.create(uri.stringValue)

	implicit def stringToStringLiteral(label: String)(implicit factory: ValueFactory): Literal =
		factory.createLiteral(label, XMLSchema.STRING)

	implicit class EnrichedSesameUri(val uri: IRI) extends AnyVal{
		def ===(other: IRI): Boolean = uri == other
		def ===(other: JavaUri): Boolean = sesameUriToJava(uri) == other
	}

	implicit class EnrichedJavaUri(val uri: JavaUri) extends AnyVal{
		def ===(other: IRI): Boolean = sesameUriToJava(other) == uri
		def ===(other: JavaUri): Boolean = uri == other
	}

	implicit class IterableRepositoryResult[T](val res: RepositoryResult[T]) extends AnyVal{
		def asScalaIterator: CloseableIterator[T] = new SesameIterationIterator(res, () => ())
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

			val finalCleanup = () => {
				conn.close()
				extraCleanup()
			}

			try{
				val repRes = accessor(conn)
				new SesameIterationIterator(repRes, finalCleanup)
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