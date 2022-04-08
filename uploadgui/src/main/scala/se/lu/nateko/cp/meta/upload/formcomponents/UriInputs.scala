package se.lu.nateko.cp.meta.upload.formcomponents

import java.net.URI
import scala.util.{ Success, Try, Failure }
import se.lu.nateko.cp.meta.upload.Utils._

class UriOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[URI](elemId, cb)(UriInput.parser(_).map(Some(_)), _.toString())

class UriOptionalOneOrSeqInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Either[URI, Seq[URI]]](elemId, cb)(s =>
	if(s.isEmpty) Success(None)
	else if(s.contains("\n")) Try(Some(Right(UriListInput.parser(s).get)))
	else Try(Some(Left(UriInput.parser(s).get))),
	_ match {
		case Left(value) => value.toString()
		case Right(value) => value.mkString("\n")
	})

class UriInput(elemId: String, cb: () => Unit) extends GenericTextInput[URI](elemId, cb, fail("Malformed URL (must start with http[s]://)"))(
	UriInput.parser, uri => uri.toString()
)

object UriInput {
	def parser(s: String): Try[URI] = {
		if(s.startsWith("https://") || s.startsWith("http://")) Try(new URI(s))
		else Failure(new Exception("Malformed URL (must start with http[s]://)"))
	}
}

class UriListInput(elemId: String, cb: () => Unit) extends GenericTextInput[Seq[URI]](elemId, cb, Success(Nil))(
	UriListInput.parser,
	UriListInput.serializer
)

class NonEmptyUriListInput(elemId: String, cb: () => Unit) extends GenericTextInput[Seq[URI]](elemId, cb, UriListInput.emptyError)(
	s => UriListInput.parser(s).flatMap(
		uris => if(uris.isEmpty) UriListInput.emptyError else Success(uris)
	),
	UriListInput.serializer
)

object UriListInput{

	def parser(value: String): Try[Seq[URI]] = Try(
		value.split("\n").map(_.trim).filterNot(_.isEmpty)
			.map(line => UriInput.parser(line).get).toIndexedSeq
	)

	def serializer = {
		(list: Seq[URI]) => list.map(_.toString).mkString("\n")
	}

	val emptyError = fail(s"uri list cannot be empty")
}
