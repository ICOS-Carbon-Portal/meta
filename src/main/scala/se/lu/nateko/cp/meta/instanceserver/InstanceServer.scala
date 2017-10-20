package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model._
import org.eclipse.rdf4j.model.vocabulary.{RDF, XMLSchema}
import scala.util.Try
import se.lu.nateko.cp.meta.api.CloseableIterator

trait InstanceServer {
	import InstanceServer._

	/**
	 * Makes a new IRI for the new instance, but does not add any triples to the repository.
	 * @param prefix The prefix to start the new IRI with
	 */
	def makeNewInstance(prefix: IRI): IRI
	def readContexts: Seq[IRI]
	def writeContexts: Seq[IRI]
	def factory: ValueFactory

	def getStatements(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): CloseableIterator[Statement]
	def hasStatement(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): Boolean
	def filterNotContainedStatements(statements: TraversableOnce[Statement]): Seq[Statement]
	def applyAll(updates: Seq[RdfUpdate]): Try[Unit]
	def writeContextsView: InstanceServer

	def shutDown(): Unit = {}

	final def addAll(statements: Seq[Statement]): Try[Unit] = applyAll(statements.map(RdfUpdate(_, true)))
	final def removeAll(statements: Seq[Statement]): Try[Unit] = applyAll(statements.map(RdfUpdate(_, false)))

	final def getInstances(classUri: IRI): IndexedSeq[IRI] =
		getStatements(None, Some(RDF.TYPE), Some(classUri))
			.map(_.getSubject)
			.collect{case uri: IRI => uri}
			.toIndexedSeq

	final def getStatements(instUri: IRI): IndexedSeq[Statement] = getStatements(Some(instUri), None, None).toIndexedSeq

	final def getValues(instUri: IRI, propUri: IRI): IndexedSeq[Value] =
		getStatements(Some(instUri), Some(propUri), None)
			.map(_.getObject)
			.toIndexedSeq

	final def hasStatement(subject: IRI, predicate: IRI, obj: Value): Boolean =
		hasStatement(Some(subject), Some(predicate), Some(obj))

	final def add(statements: Statement*): Try[Unit] = addAll(statements)
	final def remove(statements: Statement*): Try[Unit] = removeAll(statements)

	final def addInstance(instUri: IRI, classUri: IRI): Try[Unit] =
		add(factory.createStatement(instUri, RDF.TYPE, classUri))

	final def removeInstance(instUri: IRI): Unit = removeAll(getStatements(instUri))

	final def addPropertyValue(instUri: IRI, propUri: IRI, value: Value): Try[Unit] =
		add(factory.createStatement(instUri, propUri, value))

	final def removePropertyValue(instUri: IRI, propUri: IRI, value: Value): Try[Unit] =
		remove(factory.createStatement(instUri, propUri, value))

	final def applyDiff(from: Seq[Statement], to: Seq[Statement]): Unit = {
		val toRemove = from.diff(to)
		val toAdd = to.diff(from)

		applyAll(toRemove.map(RdfUpdate(_, false)) ++ toAdd.map(RdfUpdate(_, true)))
	}

	final def getUriValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): Seq[IRI] = {
		val values = getValues(subj, pred).collect{case uri: IRI => uri}
		assertCardinality(values.size, exp, s"IRI value(s) of $pred for $subj")
		values
	}

	final def getLiteralValues(subj: IRI, pred: IRI, dType: IRI, exp: CardinalityExpectation = Default): Seq[String] = {
		val values = getValues(subj, pred).collect{
			case lit: Literal if(lit.getDatatype == dType) => lit.stringValue
		}
		assertCardinality(values.size, exp, s"${dType.getLocalName} value(s) of $pred for $subj")
		values
	}

	final def getStringValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): Seq[String] =
		getLiteralValues(subj, pred, XMLSchema.STRING, exp)

	final def getIntValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): Seq[Int] =
		getLiteralValues(subj, pred, XMLSchema.INTEGER, exp).map(_.toInt)

	final def getLongValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): Seq[Long] =
		getLiteralValues(subj, pred, XMLSchema.LONG, exp).map(_.toLong)

	final def getDoubleValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): Seq[Double] =
		getLiteralValues(subj, pred, XMLSchema.DOUBLE, exp).map(_.toDouble)

}

object InstanceServer{
	sealed trait CardinalityExpectation
	case object AtMostOne extends CardinalityExpectation
	case object AtLeastOne extends CardinalityExpectation
	case object ExactlyOne extends CardinalityExpectation
	case object Default extends CardinalityExpectation

	private def assertCardinality(actual: Int, expectation: CardinalityExpectation, errorTip: => String): Unit = {
		expectation match{
			case Default => ()
			case AtMostOne => assert(actual <= 1, s"Expected at most one $errorTip, but got $actual")
			case AtLeastOne => assert(actual >= 1, s"Expected at least one $errorTip, but got $actual")
			case ExactlyOne => assert(actual == 1, s"Expected exactly one $errorTip, but got $actual")
		}
	}
}
