package se.lu.nateko.cp.meta.utils.rdf4j

import scala.language.unsafeNulls

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import se.lu.nateko.cp.meta.api.CloseableIterator

/**
 * RDF4J helpers used only by meta: statement production (`RdfMaker`, the ingesters) and
 * `UriSerializer`. Same package as rdf-common's rdf4j utilities so call-site imports are
 * unchanged; the file must not be named `package.scala`, which would collide with rdf-common's
 * synthetic `package$package` class in this package.
 */

extension(factory: ValueFactory)
	def tripleToStatement(triple: (IRI, IRI, Value)): Statement =
		factory.createStatement(triple._1, triple._2, triple._3)

extension [T](res: CloseableIteration[T])
	def asCloseableIterator: CloseableIterator[T] = new Rdf4jIterationIterator(res)
