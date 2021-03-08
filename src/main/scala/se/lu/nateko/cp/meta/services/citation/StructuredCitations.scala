package se.lu.nateko.cp.meta.services.citation

import se.lu.nateko.cp.meta.core.data._

class StructuredCitations(dobj: DataObject, citInfo: CitationInfo, keywords: Option[IndexedSeq[String]]){

	private type TagOpt = (String, Option[String])

	private val note: Option[String] = dobj.specificInfo.fold(
		l3 => l3.description,
		_ => None
	)
	private val newLine = "\r\n"

	def toBibTex: String = {
		// http://www.bibtex.org/Format/
		// http://bib-it.sourceforge.net/help/fieldsAndEntryTypes.php

		val key: String = citInfo.pidUrl.getOrElse(dobj.fileName)

		val authorsOpt = citInfo.authors.map{
			_.map(p => s"${p.lastName}, ${p.firstName.head}.").mkString(", ")
		}

		val kwords = keywords.filterNot(_.isEmpty).map(kws => kws.mkString(", "))

		val tagsOpt: Seq[TagOpt] = Seq(
			"author" -> authorsOpt,
			"title" -> citInfo.title,
			"year" -> citInfo.year,
			"note" -> note,
			"keywords" -> kwords,
			"url" -> citInfo.pidUrl,
			"publisher" -> Some(dobj.submission.submitter.name),
			"copyright" -> Some("https://creativecommons.org/licenses/by/4.0/"),
			"doi" -> dobj.doi,
			"pid" -> dobj.pid,
		)

		tagsOpt
			.collect{
				case (key, Some(value)) => s"${key}={$value}"
			}
			.mkString(s"@misc{$key,", ",", "}")
	}

	// https://en.wikipedia.org/wiki/RIS_(file_format)
	def toRis: String = {

		val authorsOpt: Seq[TagOpt] = citInfo.authors
			.map{
				_.map(p => "AU" -> Some(s"${p.lastName}, ${p.firstName.head}."))
			}
			.getOrElse(Nil)

		val kwords: Seq[TagOpt] = keywords.map{
			_.map(word => "KW" -> Some(word))
		}.getOrElse(Nil)

		val startTag = Seq("TY" -> Some("DATA"))
		val endTag = "ER" -> Some("")

		val tagsOpt: Seq[TagOpt] = Seq(
			"T1" -> citInfo.title,
			"ID" -> dobj.pid,
			"DO" -> dobj.doi,
			"PY" -> citInfo.year,
			"AB" -> note,
			"UR" -> citInfo.pidUrl,
			"PB" -> Some(dobj.submission.submitter.name)
		)

		(startTag ++ tagsOpt ++ authorsOpt ++ kwords :+ endTag)
			.collect{
				case (key, Some(value)) => s"${key} - $value"
			}
			.mkString(newLine)
	}
}
