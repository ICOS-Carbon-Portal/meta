package se.lu.nateko.cp.meta.utils

import scala.language.unsafeNulls

import scala.collection.mutable.Buffer

/**
 * Helpers used only by meta: `UploadDtoReader`, `TimeSeriesUploadCompleter` and `EtcMetaSource`
 * respectively. Same package as rdf-common's general utilities so call-site imports are
 * unchanged; the file must not be named `package.scala`, which would collide with rdf-common's
 * synthetic `package$package` class in this package.
 */

def transformEither[L0, R0, L, R](left: L0 => L, right: R0 => R)(either: Either[L0, R0]): Either[L, R] =
	either.fold[Either[L, R]](l => Left(left(l)), r => Right(right(r)))

def printAsJsonArray(ss: Seq[String]): String = {
	import spray.json.{JsArray, JsString}
	JsArray(ss.map(s => JsString(s)).toVector).prettyPrint
}

def slidingByKey[T >: Null, K](inner: Iterator[T])(key: T => K) = new Iterator[IndexedSeq[T]]{
	private val group = Buffer.empty[T]

	def hasNext: Boolean = !group.isEmpty || {
		if(inner.hasNext) {
			group.append(inner.next())
			true
		}
		else false
	}

	def next(): IndexedSeq[T] =
		if(!hasNext)
			throw new NoSuchElementException("slidingByKey iterator empty")
		else {
			val lastKey = key(group.last)
			var next: T = null
			while(inner.hasNext && {next = inner.next(); key(next) == lastKey}){
				group.append(next)
				next = null
			}
			val nextGroup = group.toIndexedSeq
			group.clear()
			if(next != null) group.append(next)
			nextGroup
		}

}
