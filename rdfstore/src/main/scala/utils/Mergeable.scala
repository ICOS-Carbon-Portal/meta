package se.lu.nateko.cp.meta.utils

import akka.Done

/**
 * Combining two `Validated`s of the same type. Both this and `mergeValidated` used to live in
 * rdf-common (the latter as `Validated.merge`), but `CitationClient`'s warm-up is the only user
 * of either, and `Mergeable` exists solely to serve it.
 */
trait Mergeable[T]:
	def merge(l: T, r: T): T

object Mergeable:
	given Mergeable[Done] with
		def merge(d1: Done, d2: Done) = Done

	def mergeValidated[T](l: Validated[T], r: Validated[T])(using m: Mergeable[T]): Validated[T] =
		val res = (l.result ++ r.result).reduceOption(m.merge)
		new Validated(res, l.errors ++ r.errors)
