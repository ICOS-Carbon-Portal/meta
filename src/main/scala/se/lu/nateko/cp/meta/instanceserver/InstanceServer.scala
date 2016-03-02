package se.lu.nateko.cp.meta.instanceserver

import org.openrdf.model._
import org.openrdf.model.vocabulary.{RDF, RDFS, XMLSchema}
import scala.annotation.migration
import scala.util.Try
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.utils.sesame._

trait InstanceServer {
	import InstanceServer._

	/**
	 * Makes a new URI for the new instance, but does not add any triples to the repository.
	 * @param prefix The prefix to start the new URI with
	 */
	def makeNewInstance(prefix: URI): URI
	def readContexts: Seq[URI]
	def writeContexts: Seq[URI]
	def factory: ValueFactory

	def getStatements(subject: Option[URI], predicate: Option[URI], obj: Option[Value]): CloseableIterator[Statement]
	def hasStatement(subject: Option[URI], predicate: Option[URI], obj: Option[Value]): Boolean
	def filterNotContainedStatements(statements: TraversableOnce[Statement]): Seq[Statement]
	def applyAll(updates: Seq[RdfUpdate]): Try[Unit]

	def shutDown(): Unit = {}

	final def addAll(statements: Seq[Statement]): Try[Unit] = applyAll(statements.map(RdfUpdate(_, true)))
	final def removeAll(statements: Seq[Statement]): Try[Unit] = applyAll(statements.map(RdfUpdate(_, false)))

	final def getInstances(classUri: URI): Seq[URI] =
		getStatements(None, Some(RDF.TYPE), Some(classUri))
			.map(_.getSubject)
			.collect{case uri: URI => uri}
			.toIndexedSeq

	final def getStatements(instUri: URI): Seq[Statement] = getStatements(Some(instUri), None, None).toIndexedSeq

	final def getValues(instUri: URI, propUri: URI): Seq[Value] =
		getStatements(Some(instUri), Some(propUri), None)
			.map(_.getObject)
			.toIndexedSeq

	final def hasStatement(subject: URI, predicate: URI, obj: Value): Boolean =
		hasStatement(Some(subject), Some(predicate), Some(obj))

	final def add(statements: Statement*): Try[Unit] = addAll(statements)
	final def remove(statements: Statement*): Try[Unit] = removeAll(statements)

	final def addInstance(instUri: URI, classUri: URI): Try[Unit] =
		add(factory.createStatement(instUri, RDF.TYPE, classUri))

	final def removeInstance(instUri: URI): Unit = removeAll(getStatements(instUri))

	final def addPropertyValue(instUri: URI, propUri: URI, value: Value): Try[Unit] =
		add(factory.createStatement(instUri, propUri, value))

	final def removePropertyValue(instUri: URI, propUri: URI, value: Value): Try[Unit] =
		remove(factory.createStatement(instUri, propUri, value))

	final def applyDiff(from: Seq[Statement], to: Seq[Statement]): Unit = {
		val toRemove = from.diff(to)
		val toAdd = to.diff(from)

		applyAll(toRemove.map(RdfUpdate(_, false)) ++ toAdd.map(RdfUpdate(_, true)))
	}

	final def getUriValues(subj: URI, pred: URI, exp: CardinalityExpectation = Default): Seq[URI] = {
		val values = getValues(subj, pred).collect{case uri: URI => uri}
		assertCardinality(values.size, exp, s"URI value(s) of $pred for $subj")
		values
	}

	final def getLiteralValues(subj: URI, pred: URI, dType: URI, exp: CardinalityExpectation = Default): Seq[String] = {
		val values = getValues(subj, pred).collect{
			case lit: Literal if(lit.getDatatype == dType) => lit.stringValue
		}
		assertCardinality(values.size, exp, s"${dType.getLocalName} value(s) of $pred for $subj")
		values
	}

	final def getStringValues(subj: URI, pred: URI, exp: CardinalityExpectation = Default): Seq[String] =
		getLiteralValues(subj, pred, XMLSchema.STRING, exp)

	final def getIntValues(subj: URI, pred: URI, exp: CardinalityExpectation = Default): Seq[Int] =
		getLiteralValues(subj, pred, XMLSchema.INTEGER, exp).map(_.toInt)

	final def getDoubleValues(subj: URI, pred: URI, exp: CardinalityExpectation = Default): Seq[Double] =
		getLiteralValues(subj, pred, XMLSchema.DOUBLE, exp).map(_.toDouble)

}

object InstanceServer{
	sealed trait CardinalityExpectation
	case object AtMostOne extends CardinalityExpectation
	case object ExactlyOne extends CardinalityExpectation
	case object Default extends CardinalityExpectation

	private def assertCardinality(actual: Int, expectation: CardinalityExpectation, errorTip: => String): Unit = {
		expectation match{
			case Default => ()
			case AtMostOne => assert(actual <= 1, s"Expected at most one $errorTip, but got $actual")
			case ExactlyOne => assert(actual == 1, s"Expected exactly one $errorTip, but got $actual")
		}
	}
}
