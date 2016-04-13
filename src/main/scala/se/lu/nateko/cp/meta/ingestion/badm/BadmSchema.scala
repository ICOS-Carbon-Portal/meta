package se.lu.nateko.cp.meta.ingestion.badm

import BadmSchemaParser.BadmVarVocab

case class AncillaryValue(label: String, comment: Option[String])
case class PropertyInfo(label: String, comment: Option[String], hasVocab: Boolean)

case class BadmSchema(
	properties: Map[String, PropertyInfo],
	vocabs: Map[String, BadmVarVocab]
)

