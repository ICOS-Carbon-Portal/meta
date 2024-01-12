package se.lu.nateko.cp.meta.utils.rdf4j

import java.net.{ URI => JavaUri }
import java.time.Instant

import scala.collection.AbstractIterator
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.common.transaction.IsolationLevel
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.SailConnection

import se.lu.nateko.cp.meta.api.CloseableIterator
import akka.http.scaladsl.model.Uri

extension(factory: ValueFactory){
	def createIRI(uri: JavaUri): IRI = factory.createIRI(uri.toString)
	def createIRI(base: JavaUri, fragment: String): IRI = factory.createIRI(base.toString, fragment)
	def createIRI(base: IRI, fragment: String): IRI = factory.createIRI(base.stringValue, fragment)
	def createLiteral(label: String, dtype: JavaUri): Literal = factory.createLiteral(label, createIRI(dtype))
	def createDateTimeLiteral(dt: Instant): Literal = factory.createLiteral(dt.toString, XSD.DATETIME)
	def createStringLiteral(label: String): Literal = factory.createLiteral(label, XSD.STRING)

	def tripleToStatement(triple: (IRI, IRI, Value)): Statement =
		factory.createStatement(triple._1, triple._2, triple._3)
}

extension (label: String)(using factory: ValueFactory){
	def toRdf: Literal = factory.createLiteral(label, XSD.STRING)
}

extension (uri: IRI){
	def toJava: JavaUri = JavaUri.create(uri.stringValue)
	def ===(other: IRI): Boolean = uri == other
	def ===(other: JavaUri): Boolean = toJava === other
}

extension (uri: JavaUri){
	def toRdf(using factory: ValueFactory): IRI = factory.createIRI(uri)
	def ===(other: IRI): Boolean = ===(other.toJava)
	def ===(other: JavaUri): Boolean = uri.toString == other.toString
}

extension (uri: Uri)
	def toRdf(using factory: ValueFactory): IRI = factory.createIRI(uri.toString)

extension [T](res: CloseableIteration[T, _])
	def asPlainScalaIterator: Iterator[T] = new AbstractIterator[T]{
		override def hasNext: Boolean = res.hasNext()
		override def next(): T = res.next()
	}

	def asCloseableIterator: CloseableIterator[T] = new Rdf4jIterationIterator(res)


extension (repo: Repository)

	def transact(action: RepositoryConnection => Unit): Try[Unit] = transact(action, None)
	def transact(action: RepositoryConnection => Unit, isoLevel: Option[IsolationLevel]): Try[Unit] =
		Using(repo.getConnection){conn =>
			isoLevel.fold(conn.begin)(conn.begin)
			try
				action(conn)
				conn.commit()
			catch
				case err: Throwable =>
					conn.rollback()
					throw err
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

	def accessEagerly[T](accessor: RepositoryConnection => T): T =
		val conn = repo.getConnection()
		try accessor(conn) finally conn.close()

end extension


extension (sail: Sail)
	def accessEagerly[T](accessor: SailConnection => T): T =
		val conn = sail.getConnection()
		try accessor(conn) finally conn.close()


def asString(lit: Literal): Option[String] = if(lit.getDatatype === XSD.STRING) Some(lit.stringValue) else None

def asLong(lit: Literal): Option[Long] = if(lit.getDatatype === XSD.LONG) Try(lit.longValue).toOption else None
def asFloat(lit: Literal): Option[Float] = if(lit.getDatatype === XSD.FLOAT) Try(lit.floatValue).toOption else None

def asTsEpochMillis(lit: Literal): Option[Long] = if(lit.getDatatype === XSD.DATETIME)
	Try(Instant.parse(lit.stringValue).toEpochMilli).toOption
else None
