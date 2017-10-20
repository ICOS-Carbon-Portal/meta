package se.lu.nateko.cp.meta.services.upload.completion

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.core.data.WdcggUploadCompletion
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UploadCompletionException
import se.lu.nateko.cp.meta.utils.rdf4j.EnrichedValueFactory


private class WdcggUploadCompleter(
	val server: InstanceServer,
	vocab: CpVocab,
	metaVocab: CpmetaVocab
)(implicit ctxt: ExecutionContext) extends FormatSpecificCompleter with FetchingHelper {

	import WdcggUploadCompleter._

	private val factory = vocab.factory

	def getUpdates(hash: Sha256Sum, info: UploadCompletionInfo): Future[Seq[RdfUpdate]] = info.ingestionResult match {
//TODO Add support for idempotence here
		case Some(WdcggUploadCompletion(nRows, interVal, keyValues)) => Future{
			val facts = scala.collection.mutable.Queue.empty[(IRI, IRI, Value)]

			val objUri = vocab.getDataObject(hash)
			facts += ((objUri, metaVocab.hasNumberOfRows, vocab.lit(nRows.toLong)))

			val acquisitionUri = vocab.getAcquisition(hash)
			facts += ((acquisitionUri, metaVocab.prov.startedAtTime, vocab.lit(interVal.start)))
			facts += ((acquisitionUri, metaVocab.prov.endedAtTime, vocab.lit(interVal.stop)))

			val station = getSingleUri(acquisitionUri, metaVocab.prov.wasAssociatedWith)
			if(!server.hasStatement(Some(station), None, None)){
				facts ++= getStationFacts(station, keyValues)
			}

			for((key, value) <- keyValues if !specialPropKeys.contains(key)){
				val keyProp = vocab.getRelative("wdcgg/", key)

				if(!server.hasStatement(Some(keyProp), None, None)){
					facts += ((keyProp, RDF.TYPE, OWL.DATATYPEPROPERTY))
					facts += ((keyProp, RDFS.SUBPROPERTYOF, metaVocab.hasFormatSpecificMeta))
					facts += ((keyProp, RDFS.LABEL, vocab.lit(key)))
				}
				facts += ((objUri, keyProp, vocab.lit(value)))
			}
			facts.map(triple => RdfUpdate(factory.tripleToStatement(triple), true))
		}

		case None => Future.successful(Nil)

		case _ => Future.failed(new UploadCompletionException(
			s"Encountered wrong type of upload completion info, must be WdcggUploadCompletion, got $info"
		))
	}

	def finalize(hash: Sha256Sum): Future[Report] = Future.successful(
		Report(vocab.getDataObject(hash).stringValue)
	)

	private def getStationFacts(station: IRI, keyValues: Map[String, String]): Seq[(IRI, IRI, Value)] = {
		def doubleOpt(key: String): Option[Double] = keyValues.get(key).flatMap{v =>
			Try(v.toDouble).toOption
		}
		Seq[Option[(IRI, IRI, Value)]](
			Some((station, RDF.TYPE, metaVocab.stationClass)),
			keyValues.get(StationName).map(stName => (station, metaVocab.hasName, vocab.lit(stName))),
			keyValues.get(Country).map(country => (station, metaVocab.country, vocab.lit(country))),
			doubleOpt(Lat).map(lat => (station, metaVocab.hasLatitude, vocab.lit(lat))),
			doubleOpt(Lon).map(lon => (station, metaVocab.hasLongitude, vocab.lit(lon)))
		).flatten
	}
}

private object WdcggUploadCompleter{
	val StationName = "STATION NAME"
	val Country = "COUNTRY/TERRITORY"
	val Lat = "LATITUDE"
	val Lon = "LONGITUDE"

	val specialPropKeys = Set(StationName, Country, Lat, Lon)
}
