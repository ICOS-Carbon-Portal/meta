package se.lu.nateko.cp.meta.core.sparql

import java.net.URI

type Binding = Map[String, BoundValue]

sealed trait BoundValue
final case class BoundLiteral(value: String, datatype: Option[URI]) extends BoundValue
final case class BoundUri(value: URI) extends BoundValue

final case class SparqlResultHead(vars: Seq[String])
final case class SparqlResultResults(bindings: Seq[Binding])
final case class SparqlSelectResult(head: SparqlResultHead, results: SparqlResultResults)
