package se.lu.nateko.cp.meta.ingestion.badm

import java.io.InputStream

object BadmSchema{

	type BadmVarVocab = Map[String, AncillaryValue]
	type Schema = Map[String, PropertyInfo]
	type Rows = Seq[Array[String]]

	case class AncillaryValue(label: String, comment: Option[String])
	case class PropertyInfo(label: String, comment: Option[String], vocab: Option[BadmVarVocab]){
		def hasVocab = vocab.isDefined
	}

	def parseSchemaFromCsv(vars: InputStream, vocab: InputStream): Schema =
		parseSchemaFromCsv(Parser.getCsvRows(vars), Parser.getCsvRows(vocab))

	def parseSchemaFromCsv(vars: Rows, vocab: Rows): Schema = {
		makeSchema(vars, makeVocabs(vocab))
	}

	private def makeVocabs(vocabRows: Rows): Map[String, BadmVarVocab] = {
		vocabRows.map{row =>
			val value = row(1)
			val explicitLabel = row(2)
			val explicitComment = row(3)
			new BadmRow(
				variable = row(0),
				vocabValue = value,
				label = if(explicitLabel.isEmpty) value else explicitLabel,
				comment = (if(explicitComment.isEmpty) None else Some(explicitComment))
			)
		}.groupBy(_.variable)
		 .collect{
			case(variable, rows) if !variable.isEmpty =>
				val vocab: BadmVarVocab = rows.groupBy(_.vocabValue).map{
					case (vocabValue, rows) =>
						val ancillaryValue = AncillaryValue(rows.head.label, rows.head.comment)
						(vocabValue, ancillaryValue)
				}
				(variable, vocab)
		}
	}

	private def makeSchema(varRows: Rows, vocabs: Map[String, BadmVarVocab]): Schema = {
		varRows.map{row =>
			val variable = row(0)
			val comment = if(row(3).isEmpty) None else Some(row(3))
			(variable, PropertyInfo(row(2), comment, vocabs.get(variable)))
		}.toMap
	}

	private class BadmRow(val variable: String, val vocabValue: String, val label: String, val comment: Option[String])
}
