package se.lu.nateko.cp.meta.utils

import scala.language.unsafeNulls

import scala.collection.mutable.Buffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class Validated[+T](val result: Option[T], val errors: Seq[String] = Nil):

	def require(errMsg: String) = if(result.isDefined) this else
		new Validated(result, errors :+ errMsg)

	def require(test: T => Boolean, errMsg: String) = if(result.map(test).getOrElse(true)) this else
		new Validated(result, errors :+ errMsg)

	def optional: Validated[Option[T]] =
		if result.isDefined
		then new Validated(Some(result), errors)
		else Validated.ok(None)

	def orElse[U >: T](fallback: => U) =
		if(result.isDefined) this
		else new Validated[U](Some(fallback), errors)

	def or[U >: T](alt: => Validated[U]): Validated[U] =
		if(result.isDefined) this else alt

	def map[U](f: T => U): Validated[U] = tryTransform(new Validated(result.map(f), errors))

	def flatMap[U](f: T => Validated[U]): Validated[U] = tryTransform{
		val valOpt = result.map(f)
		val newRes = valOpt.flatMap(_.result)
		val newErrors = errors ++ valOpt.map(_.errors).getOrElse(Nil)
		new Validated(newRes, newErrors)
	}

	def collect[U](f: PartialFunction[T, U]): Validated[U] = new Validated(result.collect(f), errors)

	def foreach[U](f: T => U): Unit = result.foreach(f)

	def filter(p: T => Boolean) = tryTransform(new Validated(result.filter(p), errors))

	def withExtraError(msg: String) = new Validated(result, errors :+ msg)

	def getOrThrow[E <: Throwable](exc: String => E): T = toTry(exc).get

	def toTry[E <: Throwable](exc: String => E): Try[T] = result match
		case Some(value) => Success(value)
		case None =>
			val msg = if errors.isEmpty then "no element found"
				else errors.mkString(";\n")
			Failure(exc(msg))

	@inline final def withFilter(p: T => Boolean): WithFilter = new WithFilter(p)

	final class WithFilter(p: T => Boolean) {
		def map[U](f:     T => U): Validated[U]                 = Validated.this filter p map f
		def flatMap[U](f: T => Validated[U]): Validated[U]      = Validated.this filter p flatMap f
		def foreach[U](f: T => U): Unit                         = Validated.this filter p foreach f
		def withFilter(q: T => Boolean): WithFilter             = new WithFilter(x => p(x) && q(x))
	}

	private def tryTransform[U](body: => Validated[U]): Validated[U] =
		try{
			body
		}catch{
			case err: Throwable =>
				val msgBase = err.getMessage
				val msg = if(msgBase != null) msgBase else {
					("(exception message is null)" +: err.getStackTrace()).mkString("\n")
				}
				new Validated[U](None, errors :+ s"${err.getClass.getName}: $msg")
		}

end Validated

object Validated:

	enum CardinalityExpectation(val descr: String):
		case AtMostOne extends CardinalityExpectation("at most one")
		case AtLeastOne extends CardinalityExpectation("at least one")
		case ExactlyOne extends CardinalityExpectation("exactly one")
		case Default extends CardinalityExpectation("any amount")


	def apply[T](v: => T): Validated[T] =
		try ok(v) catch case err: Throwable =>
			new Validated(None, Seq(err.getMessage))

	def ok[T](v: T) = new Validated(Option(v))
	def error[T](errorMsg: String) = new Validated[T](None, Seq(errorMsg))

	def fromTry[T](t: Try[T]): Validated[T] = t.fold(err => error(err.getMessage), ok)

	def sequence[T](valids: IterableOnce[Validated[T]]): Validated[IndexedSeq[T]] = {
		val res = Buffer.empty[T]
		val errs = Buffer.empty[String]

		valids.iterator.foreach{valid =>
			res ++= valid.result
			errs ++= valid.errors
		}

		new Validated(Some(res.toIndexedSeq), errs.toSeq)
	}

	extension[T] (v: Validated[Validated[T]])
		def flatten: Validated[T] = v.flatMap(identity)

	extension[T] (v: Validated[Future[T]])
		def liftFuture(using ExecutionContext): Future[Validated[T]] = v.result
			.fold(Future.successful(new Validated[T](None, v.errors))):
				_.map(res => new Validated[T](Some(res), v.errors))

	extension[T] (o: Option[Validated[T]])
		def sinkOption: Validated[Option[T]] = o match
			case None => ok(None)
			case Some(v) => v.map(Some(_))

	extension[T] (o: Option[T])
		def getOrElseV(v: => Validated[T]): Validated[T] = o match
			case Some(t) => Validated.ok(t)
			case None => v

	extension[T] (s: IndexedSeq[T])
		def validateSize(card: CardinalityExpectation, errorMsg: => String): Validated[IndexedSeq[T]] =
			import CardinalityExpectation.{AtLeastOne, AtMostOne, ExactlyOne, Default}
			def error: Validated[IndexedSeq[T]] =
				new Validated(Some(s).filterNot(_.isEmpty), Seq(errorMsg))

			card match
				case AtMostOne  if s.length  > 1 =>
					error
				case AtLeastOne if s.length  < 1 =>
					error
				case ExactlyOne if s.length != 1 =>
					error
				case Default | AtLeastOne | AtMostOne | ExactlyOne =>
					Validated.ok(s)

	def merge[T](l: Validated[T], r: Validated[T])(using m: Mergeable[T]) =
		val res = (l.result ++ r.result).reduceOption(m.merge)
		new Validated(res, l.errors ++ r.errors)

end Validated
