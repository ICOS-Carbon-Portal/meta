package se.lu.nateko.cp.meta.utils

import java.net.{ URI => JavaUri }
import java.time.Instant

import scala.collection.AbstractIterator
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.eclipse.rdf4j.IsolationLevel
import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.SailConnection

import se.lu.nateko.cp.meta.api.CloseableIterator

package object rdf4j {

	implicit class EnrichedValueFactory(val factory: ValueFactory) extends AnyVal{
		def createIRI(uri: JavaUri): IRI = factory.createIRI(uri.toString)
		def createIRI(base: JavaUri, fragment: String): IRI = factory.createIRI(base.toString, fragment)
		def createIRI(base: IRI, fragment: String): IRI = factory.createIRI(base.stringValue, fragment)
		def createLiteral(label: String, dtype: JavaUri) = factory.createLiteral(label, createIRI(dtype))
		def createLiteral(dt: Instant) = factory.createLiteral(dt.toString, XMLSchema.DATETIME)
		def createStringLiteral(label: String) = factory.createLiteral(label, XMLSchema.STRING)

		def tripleToStatement(triple: (IRI, IRI, Value)): Statement =
			factory.createStatement(triple._1, triple._2, triple._3)
	}

	implicit class StringToStringLiteralConverter(val label: String) extends AnyVal{
		def toRdf(implicit factory: ValueFactory): Literal = factory.createLiteral(label, XMLSchema.STRING)
	}

	implicit class EnrichedRdf4jUri(val uri: IRI) extends AnyVal{
		def toJava: JavaUri = JavaUri.create(uri.stringValue)
		def ===(other: IRI): Boolean = uri == other
		def ===(other: JavaUri): Boolean = toJava == other
	}

	implicit class EnrichedJavaUri(val uri: JavaUri) extends AnyVal{
		def toRdf(implicit factory: ValueFactory): IRI = factory.createIRI(uri)
		def ===(other: IRI): Boolean = other.toJava == uri
		def ===(other: JavaUri): Boolean = uri == other
	}

	implicit class IterableCloseableIteration[T](val res: CloseableIteration[T, _]) extends AnyVal{
		def asPlainScalaIterator: Iterator[T] = new AbstractIterator[T]{
			override def hasNext: Boolean = res.hasNext()
			override def next(): T = res.next()
		}
	}

	implicit class Rdf4jRepoWithAccessAndTransactions(val repo: Repository) extends AnyVal{

		def transact(action: RepositoryConnection => Unit): Try[Unit] = transact(action, None)
		def transact(action: RepositoryConnection => Unit, isoLevel: Option[IsolationLevel]): Try[Unit] = {
			val conn = repo.getConnection
			isoLevel.fold(conn.begin)(conn.begin)
			
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

		def access[T](accessor: RepositoryConnection => CloseableIteration[T, _]): CloseableIterator[T] = access(accessor, () => ())

		def access[T](accessor: RepositoryConnection => CloseableIteration[T, _], extraCleanup: () => Unit): CloseableIterator[T] = {
			val conn = repo.getConnection

			val finalCleanup = () => {
				conn.close()
				extraCleanup()
			}

			try{
				val repRes = accessor(conn)
				new Rdf4jIterationIterator(repRes, finalCleanup)
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

	def asString(lit: Literal): Option[String] = if(lit.getDatatype === XMLSchema.STRING) Some(lit.stringValue) else None

	def asLong(lit: Literal): Option[Long] = if(lit.getDatatype === XMLSchema.LONG) Try(lit.longValue).toOption else None
	def asFloat(lit: Literal): Option[Float] = if(lit.getDatatype === XMLSchema.FLOAT) Try(lit.floatValue).toOption else None

	def asTsEpochMillis(lit: Literal): Option[Long] = if(lit.getDatatype === XMLSchema.DATETIME)
		Try(Instant.parse(lit.stringValue).toEpochMilli).toOption
	else None

}
