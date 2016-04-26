package se.lu.nateko.cp.meta.ingestion.badm

import java.net.URL
import se.lu.nateko.cp.meta.ingestion.Ingester

object BadmIngester{
	private[this] val urlPrefix = "https://static.icos-cp.eu/share/metadata/badm/"

	val variablesUrl = new URL(urlPrefix + "variablesHarmonized_OTC_CP.csv")
	val badmVocabsUrl = new URL(urlPrefix + "variablesHarmonizedVocab_OTC_CP.csv")
	val badmEntriesUrl = new URL(urlPrefix + "AncillaryCP_117_20160321.csv")


	def getSchemaAndValuesIngesters: (Ingester, Ingester) = {
		val schema = BadmSchema.parseSchemaFromCsv(variablesUrl.openStream(), badmVocabsUrl.openStream())

		val badmEntries = Parser.parseEntriesFromCsv(badmEntriesUrl.openStream())

		(new RdfBadmSchemaIngester(schema), new RdfBadmEntriesIngester(badmEntries, schema))
	}
}