package se.lu.nateko.cp.meta.utils.rdf4j

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.model.{Literal, ValueFactory}

import java.time.Instant
import scala.util.Try

/**
 * Typed literal accessors, and the datetime-literal constructor, used exclusively by rdfStore's
 * SPARQL index and filter-fusion code (`FilterPatternSearch`, `DofPatternFusion`, `IndexData`,
 * `CpEvaluationStrategyFactory`). They live here rather than in rdf-common because nothing in
 * meta, and nothing in the shared read stack, has ever needed them.
 *
 * Same package as rdf-common's own rdf4j utilities on purpose: call sites keep importing
 * `se.lu.nateko.cp.meta.utils.rdf4j.*` unchanged. The file must not be named `package.scala`,
 * which would collide with rdf-common's synthetic `package$package` class in this package.
 */

extension(factory: ValueFactory)
	def createDateTimeLiteral(dt: Instant): Literal = factory.createLiteral(dt.toString, XSD.DATETIME)

def asString(lit: Literal): Option[String] = if(lit.getDatatype === XSD.STRING) Some(lit.stringValue) else None

def asLong(lit: Literal): Option[Long] = if(lit.getDatatype === XSD.LONG) Try(lit.longValue).toOption else None
def asFloat(lit: Literal): Option[Float] = if(lit.getDatatype === XSD.FLOAT) Try(lit.floatValue).toOption else None

def asTsEpochMillis(lit: Literal): Option[Long] = if(lit.getDatatype === XSD.DATETIME)
	Try(Instant.parse(lit.stringValue).toEpochMilli).toOption
else None
