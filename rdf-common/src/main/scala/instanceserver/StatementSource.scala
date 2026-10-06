package se.lu.nateko.cp.meta.instanceserver

import scala.language.unsafeNulls

import java.net.URI as JavaUri
import java.time.{Instant, LocalDate}
import org.eclipse.rdf4j.model.{IRI, Value, Statement, Literal}
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS, XSD}
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.UriResource
import se.lu.nateko.cp.meta.utils.rdf4j.{===, toJava}
import se.lu.nateko.cp.meta.utils.{Validated, parseInstant}
import Validated.{CardinalityExpectation, validateSize}
import CardinalityExpectation.{AtMostOne, ExactlyOne}

trait StatementSource {
	def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement]
	def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean

	final def hasStatement(st: Statement): Boolean = st.getSubject() match
		case subj: IRI => hasStatement(subj, st.getPredicate(), st.getObject())
		case _ => false
}

object StatementSource {
	def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null)(using
		source: StatementSource
	): CloseableIterator[Statement] =
		source.getStatements(subject, predicate, obj)

	def getStatements(subject: IRI)(using StatementSource): IndexedSeq[Statement] =
		getStatements(subject, null, null).toIndexedSeq

	def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null)(using
		source: StatementSource
	): Boolean =
		source.hasStatement(subject, predicate, obj)

	def resourceHasType(res: IRI, tpe: IRI)(using StatementSource): Boolean = hasStatement(res, RDF.TYPE, tpe)

	def getValues(instUri: IRI, propUri: IRI)(using StatementSource): IndexedSeq[Value] =
		getStatements(instUri, propUri, null).map(_.getObject).toIndexedSeq

	def getPropValueHolders(prop: IRI, v: Value)(using StatementSource): IndexedSeq[IRI] =
		getStatements(null, prop, v)
			.map(_.getSubject)
			.collect { case subj: IRI => subj }
			.toIndexedSeq

	def getTypes(res: IRI)(using StatementSource): IndexedSeq[IRI] = getValues(res, RDF.TYPE).collect:
		case classUri: IRI => classUri

	def getLiteralValues(subj: IRI, pred: IRI, dType: IRI)(using StatementSource): IndexedSeq[String] =
		getValues(subj, pred)
			.collect:
				case lit: Literal if (lit.getDatatype === dType) => lit.stringValue
			.distinct

	def getUriValues[C <: StatementSource](subj: IRI, pred: IRI)(using C): IndexedSeq[IRI] =
		getValues(subj, pred).collect { case uri: IRI => uri }.distinct

	def getStringValues[C <: StatementSource](subj: IRI, pred: IRI)(using C): IndexedSeq[String] =
		getLiteralValues(subj, pred, XSD.STRING)

	def getIntValues[C <: StatementSource](subj: IRI, pred: IRI)(using C): IndexedSeq[Int] =
		getLiteralValues(subj, pred, XSD.INTEGER).flatMap(_.toIntOption)

	def getLongValues[C <: StatementSource](subj: IRI, pred: IRI)(using C): IndexedSeq[Long] =
		getLiteralValues(subj, pred, XSD.LONG).flatMap(_.toLongOption)

	def getDoubleValues[C <: StatementSource](subj: IRI, pred: IRI)(using C): IndexedSeq[Double] =
		getLiteralValues(subj, pred, XSD.DOUBLE).flatMap(_.toDoubleOption)

	def getFloatValues[C <: StatementSource](subj: IRI, pred: IRI)(using C): IndexedSeq[Float] =
		getLiteralValues(subj, pred, XSD.FLOAT).flatMap(_.toFloatOption)

	def getUriLiteralValues[C <: StatementSource](subj: IRI, pred: IRI)(using C): IndexedSeq[JavaUri] =
		getLiteralValues(subj, pred, XSD.ANYURI).map(new JavaUri(_))

	def validate[T](
		getter: (IRI, IRI) => StatementSource ?=> IndexedSeq[T],
		subj: IRI,
		pred: IRI,
		card: CardinalityExpectation
	)(using StatementSource): Validated[IndexedSeq[T]] = Validated(getter(subj, pred)).flatMap: vals =>
		vals.validateSize(
			card,
			s"Expected ${card.descr} values of property $pred for resource $subj, but got ${vals.length}"
		)
	end validate

	def getSingleUri[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[IRI] =
		validate(getUriValues, subj, pred, ExactlyOne).map(_.head)

	def getOptionalUri[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Option[IRI]] =
		validate(getUriValues, subj, pred, AtMostOne).map(_.headOption)

	def getLabeledResource[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[UriResource] =
		getSingleUri(subj, pred).flatMap(getLabeledResource)

	def getLabeledResource[C <: StatementSource](uri: IRI)(using C): Validated[UriResource] =
		getOptionalString[C](uri, RDFS.LABEL).map: label =>
			UriResource(uri.toJava, label, getStringValues(uri, RDFS.COMMENT))

	def getOptionalString[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Option[String]] =
		validate(getStringValues, subj, pred, AtMostOne).map(_.headOption)

	def getSingleString[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[String] =
		validate(getStringValues, subj, pred, ExactlyOne).map(_.head)

	def getSingleInt[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Int] =
		validate(getIntValues, subj, pred, ExactlyOne).map(_.head)

	def getOptionalInt[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Option[Int]] =
		validate(getIntValues, subj, pred, AtMostOne).map(_.headOption)

	def getOptionalLong[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Option[Long]] =
		validate(getLongValues, subj, pred, AtMostOne).map(_.headOption)

	def getOptionalDouble[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Option[Double]] =
		validate(getDoubleValues, subj, pred, AtMostOne).map(_.headOption)

	def getOptionalFloat[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Option[Float]] =
		validate(getFloatValues, subj, pred, AtMostOne).map(_.headOption)

	def getOptionalBool[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Option[Boolean]] =
		validate(getLiteralValues(_, _, XSD.BOOLEAN), subj, pred, AtMostOne)
			.map(_.headOption.map(_.toLowerCase == "true"))

	def getSingleDouble[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Double] =
		validate(getDoubleValues, subj, pred, ExactlyOne).map(_.head)

	def getInstantValues[C <: StatementSource](subj: IRI, pred: IRI)(using C): IndexedSeq[Instant] =
		getLiteralValues(subj, pred, XSD.DATETIME).map(parseInstant)

	def getOptionalInstant[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Option[Instant]] =
		validate(getInstantValues, subj, pred, AtMostOne).map(_.headOption)

	def getSingleInstant[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Instant] =
		validate(getInstantValues, subj, pred, ExactlyOne).map(_.head)

	def getOptionalLocalDate[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Option[LocalDate]] =
		validate(getLiteralValues(_, _, XSD.DATE), subj, pred, AtMostOne).map(_.headOption.map(LocalDate.parse))

	def getSingleUriLiteral[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[JavaUri] =
		validate(getUriLiteralValues, subj, pred, ExactlyOne).map(_.head)

	def getOptionalUriLiteral[C <: StatementSource](subj: IRI, pred: IRI)(using C): Validated[Option[JavaUri]] =
		validate(getUriLiteralValues, subj, pred, AtMostOne).map(_.headOption)

	def getHashsum[C <: StatementSource](dataObjUri: IRI, pred: IRI)(using C): Validated[Sha256Sum] =
		for
			hashLits <- validate(getLiteralValues(_, _, XSD.BASE64BINARY), dataObjUri, pred, ExactlyOne)
			hash <- Validated.fromTry(Sha256Sum.fromBase64(hashLits.head))
		yield hash
}
