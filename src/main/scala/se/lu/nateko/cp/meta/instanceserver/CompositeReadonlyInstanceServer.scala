package se.lu.nateko.cp.meta.instanceserver

import scala.util.Try
import scala.util.Success
import org.eclipse.rdf4j.model.*
import se.lu.nateko.cp.meta.api.CloseableIterator

class CompositeReadonlyInstanceServer(first: InstanceServer, others: InstanceServer*) extends InstanceServer{

	private val parts = first :: others.toList

	def applyAll(updates: Seq[RdfUpdate])(cotransact: => Unit = ()): Try[Unit] = Try(cotransact)

	def factory: ValueFactory = first.factory

	def filterNotContainedStatements(statements: IterableOnce[Statement]): Seq[Statement] = {
		val seed = first.filterNotContainedStatements(statements)
		others.foldRight(seed)(_ filterNotContainedStatements _)
	}

	def getStatements(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): CloseableIterator[Statement] = {
		def getFrom(servs: List[InstanceServer]): CloseableIterator[Statement] = servs match{
			case Nil => CloseableIterator.empty
			case last :: Nil => last.getStatements(subject, predicate, obj)
			case head :: rest => head.getStatements(subject, predicate, obj) ++ getFrom(rest)
		}
		getFrom(parts)
	}

	def hasStatement(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): Boolean = parts.exists(_.hasStatement(subject, predicate, obj))

	def makeNewInstance(prefix: IRI): IRI = first.makeNewInstance(prefix)

	def readContexts: Seq[IRI] = parts.flatMap(_.readContexts).distinct

	def writeContexts: Seq[IRI] = Nil

	def withContexts(read: Seq[IRI], write: Seq[IRI]): InstanceServer = ???

}
