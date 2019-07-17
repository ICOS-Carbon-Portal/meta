package se.lu.nateko.cp.meta.core

package object data{

	type OptionalOneOrSeq[T] = Option[Either[T, Seq[T]]]

}
