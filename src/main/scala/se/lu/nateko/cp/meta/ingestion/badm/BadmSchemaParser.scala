package se.lu.nateko.cp.meta.ingestion.badm

import scala.io.Source

object BadmSchemaParser{

	type BadmVarVocab = Map[String, AncillaryValue]

	def makeVocabs(rows: Seq[Array[String]]): Map[String, BadmVarVocab] = {
		rows.map{row =>
			val value = row(1)
			val explicitLabel = row(2)
			val explicitComment = row(3)
			new {
				val variable = row(0)
				val vocabValue = value
				val label = if(explicitLabel.isEmpty) value else explicitLabel
				val comment = if(explicitComment.isEmpty) None else Some(explicitComment)
			}
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

	def makePropInfos(rows: Seq[Array[String]]): Map[String, PropertyInfo] = {
		rows.groupBy(row => row(0)).map{
			case (variable, rows) =>
				val row = rows.head
				val hasVocab = row(1) == "controlled voc"
				val comment = if(row(3).isEmpty) None else Some(row(3))
				(variable, PropertyInfo(row(2), comment, hasVocab))
		}
	}

	def parseSchemaFromCsv(vars: Seq[Array[String]], vocab: Seq[Array[String]]) =
		BadmSchema(makePropInfos(vars), makeVocabs(vocab))

	
}