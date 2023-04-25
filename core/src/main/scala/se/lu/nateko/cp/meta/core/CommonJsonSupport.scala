package se.lu.nateko.cp.meta.core

import java.net.URI
import java.time.Instant
import spray.json.*
import se.lu.nateko.cp.meta.core.data.TimeInterval
import java.time.{LocalDateTime, LocalDate}
import java.net.URL

trait CommonJsonSupport {

	given uriFormat: RootJsonFormat[URI] with{
		def write(uri: URI): JsValue = JsString(uri.toString)

		def read(value: JsValue): URI = value match{
			case JsString(uri) => try{
					new URI(uri)
				}catch{
					case err: Throwable => deserializationError(s"Could not parse URI from $uri", err)
				}
			case _ => deserializationError("URI string expected")
		}
	}

	given JsonFormat[URL] with{
		def write(uri: URL): JsValue = JsString(uri.toString)
		def read(value: JsValue): URL = uriFormat.read(value).toURL()
	}

	given RootJsonFormat[LocalDateTime] with{

		def write(dt: LocalDateTime) = JsString(dt.toString)

		def read(value: JsValue): LocalDateTime = value match{
			case JsString(s) => LocalDateTime.parse(s)
			case _ => deserializationError("String representation of a LocalDateTime is expected")
		}
	}

	given RootJsonFormat[LocalDate] with{

		def write(dt: LocalDate) = JsString(dt.toString)

		def read(value: JsValue): LocalDate = value match{
			case JsString(s) => LocalDate.parse(s)
			case _ => deserializationError("String representation of a LocalDate is expected")
		}
	}

	given RootJsonFormat[Instant] with{

		def write(instant: Instant) = JsString(instant.toString)

		def read(value: JsValue): Instant = value match{
			case JsString(s) => Instant.parse(s)
			case _ => deserializationError("String representation of a time instant is expected")
		}
	}

	given JsonFormat[TimeInterval] = DefaultJsonProtocol.jsonFormat2(TimeInterval.apply)

	def enumFormat[T <: reflect.Enum](valueOf: String => T, values: Array[T]) = new RootJsonFormat[T] {
		def write(v: T) = JsString(v.toString)

		def read(value: JsValue): T = value match{
			case JsString(s) =>
				try{
					valueOf(s)
				}catch{
					case _: IllegalArgumentException => deserializationError(
						"Expected one of: " + values.mkString("'", "', '", "'")
					)
				}
			case _ => deserializationError("Expected a JSON string")
		}
	}

}

extension [T: RootJsonWriter](v: T){
	def toTypedJson(typ: String) = JsObject(
		v.toJson.asJsObject.fields + (CommonJsonSupport.TypeField -> JsString(typ))
	)
}


object CommonJsonSupport:
	val TypeField = "_type"

	import scala.quoted.*

	inline def sealedTraitTypeclassLookup[T, TC[_]]: Any = ${sealedTraitTypeclassLookupImpl[T, TC]}

	private def sealedTraitTypeclassLookupImpl[T: Type, TC[_] : Type](using qctx: Quotes): Expr[Any] =
		import qctx.reflect.*

		def summonInstance(ccSym: Symbol): Expr[Any] =
			val tpe = ccSym.typeRef
			val instanceTpe = TypeRepr.of[TC].appliedTo(tpe)
			Implicits.search(instanceTpe) match
				case iss: ImplicitSearchSuccess => iss.tree.asExpr//.asInstanceOf[Expr[Any]]
				case _ => report.errorAndAbort(s"No given instance found for ${ccSym.name}") //Expr(s"not found: ${instanceTpe} \nfor $tpe \n for $ccSym")

		val traitSymbol = TypeRepr.of[T].typeSymbol
		val flags = traitSymbol.flags

		if !traitSymbol.flags.is(Flags.Sealed & Flags.Trait) then
			report.errorAndAbort("Type parameter must be a sealed trait")

		else
			val caseClassChildren = traitSymbol.children.filter(_.flags.is(Flags.Case))

			val pairs = caseClassChildren.map{cc =>
				summonInstance(cc) //Expr.ofTuple(Expr(cc.name) -> 
			}
			val expr = Expr.ofList(pairs)//.asInstanceOf[Map[String, TC[T]]]
			println("DONE:" + expr.show)
			expr
	end sealedTraitTypeclassLookupImpl

end CommonJsonSupport

import scala.compiletime.{erasedValue, summonInline}
import scala.quoted.*
import spray.json._

object SealedTraitJsonFormat {

	inline def summonJsonFormats[T <: Tuple]: List[JsonFormat[_]] = inline erasedValue[T] match {
		case _: EmptyTuple => Nil
		case _: (t *: ts) => summonInline[JsonFormat[t]].asInstanceOf[JsonFormat[_]] :: summonJsonFormats[ts]
	}

	inline def fromSealedTrait[T: Type]: JsonFormat[T] = {
		import scala.deriving.Mirror
		val formats = summonJsonFormats[Mirror.Of[T]#MirroredElemTypes]
		val subtypes = getSubtypes[T]
		new SealedTraitJsonFormat[T](formats, subtypes)
	}

	private def getSubtypes[T: Type]: List[String] =
		TypeRepr.of[T].typeSymbol.children.map(_.name.toString)

	private class SealedTraitJsonFormat[T](formats: List[JsonFormat[_]], subtypes: List[String]) extends JsonFormat[T] {
		def read(json: JsValue): T = {
		json.asJsObject.getFields("type", "value") match {
			case Seq(JsString(typeName), value) =>
			val index = subtypes.indexOf(typeName)
			if (index >= 0) {
				formats(index).asInstanceOf[JsonFormat[T]].read(value)
			} else {
				deserializationError(s"Unknown type: $typeName")
			}

			case _ => deserializationError("Expected a JSON object with 'type' and 'value' fields")
		}
		}

		def write(obj: T): JsValue = {
		val index = subtypes.indexOf(obj.getClass.getSimpleName)
		if (index >= 0) {
			JsObject(
			"type" -> JsString(subtypes(index)),
			"value" -> formats(index).asInstanceOf[JsonFormat[T]].write(obj)
			)
		} else {
			serializationError(s"Cannot serialize unknown subtype: ${obj.getClass.getName}")
		}
		}
	}
}
