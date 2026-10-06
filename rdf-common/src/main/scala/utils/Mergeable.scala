package se.lu.nateko.cp.meta.utils

import akka.Done

trait Mergeable[T]:
	def merge(l: T, r: T): T

object Mergeable:
	given Mergeable[Done] with
		def merge(d1: Done, d2: Done) = Done
