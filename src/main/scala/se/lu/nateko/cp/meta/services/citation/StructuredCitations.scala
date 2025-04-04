package se.lu.nateko.cp.meta.services.citation

import se.lu.nateko.cp.meta.core.data.*

class StructuredCitations(
	obj: StaticObject,
	citInfo: CitationInfo,
	keywords: Option[IndexedSeq[String]],
	theLicence: Licence
){

	private type TagOpt = (String, Option[String])

	private val note: Option[String] = obj match {
		case dobj: DataObject =>
			dobj.specificInfo.fold(
				l3 => l3.description,
				_ => None
			)
		case _ => None
	}

	private val titleWithTempCov = for
			title <- citInfo.title;
			tempCovDisplay <- citInfo.tempCovDisplay
		yield
			s"${title}, ${tempCovDisplay}"

	private val newLine = "\r\n"

	def toBibTex: String = {
		// http://www.bibtex.org/Format/
		// http://bib-it.sourceforge.net/help/fieldsAndEntryTypes.php

		val key: String = citInfo.pidUrl.getOrElse(obj.fileName)

		val authorsOpt = citInfo.authors.map{
			_.map {
				_ match {
					case p: Person => s"${p.lastName}, ${p.firstName}"
					case o: Organization => o.name
				}
			}
			.mkString(" and ")
		}

		val kwords = keywords.filterNot(_.isEmpty).map(kws => kws.mkString(", "))

		val tagsOpt: Seq[TagOpt] = Seq(
			"author" -> authorsOpt,
			"title" -> titleWithTempCov,
			"year" -> citInfo.year,
			"note" -> note,
			"keywords" -> kwords,
			"url" -> citInfo.pidUrl,
			"publisher" -> Some(obj.submission.submitter.name),
			"copyright" -> Some(theLicence.url.toString),
			"doi" -> obj.doi,
			"pid" -> obj.pid,
		)

		val separator = s",$newLine"
		tagsOpt
			.collect{
				case (key, Some(value)) => s"  ${key}={$value}"
			}
			.mkString(s"@misc{$key,$newLine", separator, s"$newLine}")
	}

	// https://en.wikipedia.org/wiki/RIS_(file_format)
	def toRis: String = {

		val authorsOpt: Seq[TagOpt] = citInfo.authors
			.map{
				_.map {
					_ match {
						case p: Person => "AU" -> Some(s"${p.lastName}, ${p.firstName}")
						case o: Organization => "AU" -> Some(o.name)
					}
				}
			}
			.getOrElse(Nil)

		val kwords: Seq[TagOpt] = keywords.map{
			_.map(word => "KW" -> Some(word))
		}.getOrElse(Nil)

		val startTag = Seq("TY" -> Some("DATA"))
		val endTag = "ER" -> Some("")

		val tagsOpt: Seq[TagOpt] = Seq(
			"T1" -> titleWithTempCov,
			"ID" -> obj.pid,
			"DO" -> obj.doi,
			"PY" -> citInfo.year,
			"AB" -> note,
			"UR" -> citInfo.pidUrl,
			"PB" -> Some(obj.submission.submitter.name)
		)

		(startTag ++ tagsOpt ++ authorsOpt ++ kwords :+ endTag)
			.collect{
				case (key, Some(value)) => s"${key} - $value"
			}
			.mkString(newLine)
	}
}
