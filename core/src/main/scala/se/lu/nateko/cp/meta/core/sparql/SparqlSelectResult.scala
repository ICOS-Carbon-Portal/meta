package se.lu.nateko.cp.meta.core.sparql

import java.net.URI

sealed trait BoundValue
case class BoundLiteral(value: String, datatype: Option[URI]) extends BoundValue
case class BoundUri(value: URI) extends BoundValue

case class SparqlResultHead(vars: Seq[String])
case class SparqlResultResults(bindings: Seq[Binding])
case class SparqlSelectResult(head: SparqlResultHead, results: SparqlResultResults)
