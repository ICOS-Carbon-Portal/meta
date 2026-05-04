package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.common.transaction.{IsolationLevel, TransactionSetting}
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value}
import org.eclipse.rdf4j.query.Update
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection, RepositoryReadOnlyException}
import org.eclipse.rdf4j.repository.base.{RepositoryConnectionWrapper, RepositoryWrapper}
import org.eclipse.rdf4j.rio.RDFFormat

import java.io.{File, InputStream, Reader}
import java.net.URL

/**
 * Wraps a Repository and lets us flip it into read-only mode at runtime. Once
 * `makeReadonly` is called, every subsequent connection rejects mutating
 * operations with a RepositoryReadOnlyException.
 *
 * Replaces the read-only switch that used to live on `EnrichingSail`.
 */
class ReadonlyRepository(inner: Repository) extends RepositoryWrapper(inner):

	@volatile private var readonlyMessage: Option[String] = None

	def makeReadonly(message: String): Unit =
		readonlyMessage = Some(message)

	def isReadonly: Boolean = readonlyMessage.isDefined

	override def getConnection(): RepositoryConnection =
		new ReadonlyConnection(this, inner.getConnection())

	private def deny(): Nothing =
		throw new RepositoryReadOnlyException(
			readonlyMessage.getOrElse("Repository is in read-only mode")
		)

	private class ReadonlyConnection(
		owner: ReadonlyRepository, delegate: RepositoryConnection
	) extends RepositoryConnectionWrapper(owner, delegate):

		private def guard(): Unit =
			if owner.isReadonly then owner.deny()

		override def begin(): Unit =
			guard()
			super.begin()

		override def begin(level: IsolationLevel): Unit =
			guard()
			super.begin(level)

		override def begin(settings: TransactionSetting*): Unit =
			guard()
			super.begin(settings*)

		override def add(s: Resource, p: IRI, o: Value, ctx: Resource*): Unit =
			guard()
			super.add(s, p, o, ctx*)

		override def add(st: Statement, ctx: Resource*): Unit =
			guard()
			super.add(st, ctx*)

		override def add(stmts: java.lang.Iterable[? <: Statement], ctx: Resource*): Unit =
			guard()
			super.add(stmts, ctx*)

		override def add(stmts: CloseableIteration[? <: Statement], ctx: Resource*): Unit =
			guard()
			super.add(stmts, ctx*)

		override def add(file: File, baseUri: String, fmt: RDFFormat, ctx: Resource*): Unit =
			guard()
			super.add(file, baseUri, fmt, ctx*)

		override def add(in: InputStream, baseUri: String, fmt: RDFFormat, ctx: Resource*): Unit =
			guard()
			super.add(in, baseUri, fmt, ctx*)

		override def add(r: Reader, baseUri: String, fmt: RDFFormat, ctx: Resource*): Unit =
			guard()
			super.add(r, baseUri, fmt, ctx*)

		override def add(url: URL, baseUri: String, fmt: RDFFormat, ctx: Resource*): Unit =
			guard()
			super.add(url, baseUri, fmt, ctx*)

		override def remove(s: Resource, p: IRI, o: Value, ctx: Resource*): Unit =
			guard()
			super.remove(s, p, o, ctx*)

		override def remove(st: Statement, ctx: Resource*): Unit =
			guard()
			super.remove(st, ctx*)

		override def remove(stmts: java.lang.Iterable[? <: Statement], ctx: Resource*): Unit =
			guard()
			super.remove(stmts, ctx*)

		override def remove(stmts: CloseableIteration[? <: Statement], ctx: Resource*): Unit =
			guard()
			super.remove(stmts, ctx*)

		override def clear(ctx: Resource*): Unit =
			guard()
			super.clear(ctx*)

		override def prepareUpdate(ql: QueryLanguage, query: String, baseUri: String): Update =
			guard()
			super.prepareUpdate(ql, query, baseUri)

		override def prepareUpdate(query: String): Update =
			guard()
			super.prepareUpdate(query)

end ReadonlyRepository
