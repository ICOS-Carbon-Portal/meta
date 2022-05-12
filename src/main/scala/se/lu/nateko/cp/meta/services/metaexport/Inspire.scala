package se.lu.nateko.cp.meta.services.metaexport

import se.lu.nateko.cp.meta.core.data.DataObject
import java.time.Instant
import scala.quoted.Expr
import scala.quoted.Quotes
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.utils.rdf4j.===
import se.lu.nateko.cp.meta.core.data.TimeInterval


object Inspire{
	val StandardVersion = "ISO 19115-1"
	val StandardEdition = "2004"

	val RevisionDateTime = "2022-05-11T17:00:00+02:00"

	val TopicEnvironment = "environment"
	val TopicAtmo = "climatologyMeteorologyAtmosphere"
	val TopicEco = "biota"
	val TopicOcean = "oceans"
}

class Inspire(dobj: DataObject, vocab: CpVocab) {
	export dobj.coverage

	def id: String = dobj.pid.getOrElse(dobj.hash.id)
	def hasPid: Boolean = dobj.pid.isDefined
	def title = dobj.references.title.getOrElse(dobj.fileName)

	def publication: Option[Instant] = dobj.submission.stop

	def creation: Instant = dobj.production.map(_.dateTime)
		.orElse(dobj.acquisition.flatMap(_.interval).map(_.stop))
		.getOrElse(dobj.submission.start)

	def topics: Seq[String] = {
		val theme = dobj.specification.theme.self.uri

		val extraTopics =
			if(theme === vocab.atmoTheme) Seq(Inspire.TopicAtmo)
			else if(theme === vocab.ecoTheme) Seq(Inspire.TopicEco)
			else if(theme === vocab.oceanTheme) Seq(Inspire.TopicOcean)
			else Seq.empty

		Inspire.TopicEnvironment +: extraTopics
	}

	def tempCoverage: Option[TimeInterval] = dobj.specificInfo.fold(
		stm => Some(stm.temporal.interval),
		_.acquisition.interval
	)
}
