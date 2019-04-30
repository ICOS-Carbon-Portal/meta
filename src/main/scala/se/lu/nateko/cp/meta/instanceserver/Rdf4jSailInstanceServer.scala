package se.lu.nateko.cp.meta.instanceserver

import java.util.UUID
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.utils.rdf4j._
import scala.util.Try
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.sail.Sail
import scala.util.Failure

class Rdf4jSailInstanceServer(sail: Sail) extends InstanceServer{

	val factory = sail.getValueFactory
	def readContexts: Seq[IRI] = Nil
	def writeContexts: Seq[IRI] = Nil

	def makeNewInstance(prefix: IRI): IRI =
		factory.createIRI(prefix.stringValue.stripSuffix("/") + "/", UUID.randomUUID.toString)

	def getStatements(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): CloseableIterator[Statement] =
		sail.access[Statement](conn =>
			conn.getStatements(
				subject.getOrElse(null),
				predicate.getOrElse(null),
				obj.getOrElse(null),
				false)
		)

	def hasStatement(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): Boolean =
		sail.accessEagerly(conn =>
			conn.hasStatement(
				subject.getOrElse(null),
				predicate.getOrElse(null),
				obj.getOrElse(null),
				false)
		)

	def applyAll(updates: Seq[RdfUpdate]): Try[Unit] = new Failure[Unit](
		new NotImplementedError("This is a read-only implementation of InstanceServer")
	)

	def filterNotContainedStatements(statements: TraversableOnce[Statement]): Seq[Statement] = {
		sail.accessEagerly{ conn =>
			statements.filter(st => !conn.hasStatement(st.getSubject, st.getPredicate, st.getObject, false)).toIndexedSeq
		}
	}

	def writeContextsView = this

}
