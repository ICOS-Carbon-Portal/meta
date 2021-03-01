package se.lu.nateko.cp.meta.services.citation

import se.lu.nateko.cp.meta.core.data._

class StructuredCitations(citInfo: CitationInfo, keywords: Option[IndexedSeq[String]]){

	private val note = citInfo.dobj.specificInfo.fold(
		l3 => l3.description,
		_ => None
	)
	private val newLine = "\r\n"

	def toText = {
		citInfo.citText
	}

	def toBibTex = {
		// http://www.bibtex.org/Format/
		// http://bib-it.sourceforge.net/help/fieldsAndEntryTypes.php
		val key: String = citInfo.dobj.doi.fold(
	        citInfo.pidUrl.fold(citInfo.dobj.fileName)(pid => pid)
	    )(doi => s"https://doi.org/$doi")
		val authorsOpt = citInfo.authors match {
			case Some(authors) => authors.map(p => s"${p.lastName}, ${p.firstName.head}.").mkString(", ")
			case _ => None
		}
		val kwords = keywords.fold[Option[String]](None)(kw => Some(kw.mkString(", ")))

		val tagsOpt = Map(
			"author" -> authorsOpt,
			"title" -> citInfo.title,
			"year" -> citInfo.year,
			"note" -> note,
			"keywords" -> kwords,
			"url" -> citInfo.pidUrl,
			"publisher" -> Some(citInfo.dobj.submission.submitter.name),
			"copyright" -> Some("https://creativecommons.org/licenses/by/4.0/"),
			"doi" -> citInfo.dobj.doi,
			"pid" -> citInfo.dobj.pid,
		)

		val tags = tagsOpt.collect{ case (key, Some(value)) => s"${key}={$value}" }
		Some(tags.mkString(s"@misc{$key,", ",", "}"))
	}

	def toRis = {
		// https://en.wikipedia.org/wiki/RIS_(file_format)
		val authorsOpt = citInfo.authors match {
			case Some(authors) => authors.map(p => "AU" -> s"${p.lastName}, ${p.firstName.head}.").toMap
			case _ => None
		}
		val kwords = keywords match {
			case Some(words) => words.map(word => "KW" -> Some(word))
			case _ => None
		}

		val startTag = Seq("TY" -> Some("DATA"))
		val endTag = Map("ER" -> Some(""))

		val tagsOpt = Seq(
			"T1" -> citInfo.title,
			"ID" -> citInfo.dobj.pid,
			"DO" -> citInfo.dobj.doi,
			"PY" -> citInfo.year,
			"AB" -> note,
			"UR" -> citInfo.pidUrl,
			"PB" -> Some(citInfo.dobj.submission.submitter.name)
		)

		val combinedTagsOpt = startTag ++ tagsOpt ++ authorsOpt ++ kwords ++ endTag
		val combinedTags = combinedTagsOpt.collect{ case (key, Some(value)) => s"${key} - $value" }

		Some(combinedTags.mkString(newLine))
	}
}
