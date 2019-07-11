package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.sail.Sail
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.api.CitationClient
import akka.actor.ActorSystem
import akka.stream.Materializer
import org.eclipse.rdf4j.model.Resource
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.instanceserver.Rdf4jSailInstanceServer
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.services.upload.CollectionFetcherLite
import se.lu.nateko.cp.meta.services.upload.StaticObjectFetcher
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.api.Doi
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.CpmetaConfig
import org.eclipse.rdf4j.model.Statement
import se.lu.nateko.cp.meta.utils.rdf4j._
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId

class CitationProviderFactory(conf: CpmetaConfig)(implicit system: ActorSystem, mat: Materializer){

	def getProvider(sail: Sail): CitationProvider = {

		val dois: List[Doi] = {
			val hasDoi = new CpmetaVocab(sail.getValueFactory).hasDoi
			sail
				.access[Statement]{conn =>
					conn.getStatements(null, hasDoi, null, false)
				}
				.map(_.getObject.stringValue)
				.toList.distinct.collect{
					case Doi(doi) => doi
				}
		}

		val dataCiter = new CitationClient(dois, conf.citations)
		new CitationProvider(dataCiter, sail, conf.core, conf.dataUploadService)
	}

}

class CitationProvider(val dataCiter: CitationClient, sail: Sail, coreConf: MetaCoreConfig, uploadConf: UploadServiceConfig){
	val vocab = new CpVocab(sail.getValueFactory)(coreConf.envriConfigs)

	def getCitation(maybeDobj: Resource): Option[String] = getStaticObject(maybeDobj).flatMap{dobj =>
		dobj.asDataObject.flatMap(getIcosCitation).orElse(getDataCiteCitation(dobj))
	}

	private implicit val envri = Envri.ICOS //functionality limited to ICOS for now

	private val objFetcher = {
		val pidFactory = new HandleNetClient.PidFactory(uploadConf.handle)
		val server = new Rdf4jSailInstanceServer(sail)

		val collFetcher = new CollectionFetcherLite(server, vocab)
		new StaticObjectFetcher(server, vocab, collFetcher, pidFactory)
	}

	private val objPrefix: String = vocab.staticObjectPrefix

	def getStaticObject(maybeDobj: Resource): Option[StaticObject] = maybeDobj match{

		case iri: IRI if iri.stringValue.startsWith(objPrefix) => for(
			hash <- Sha256Sum.fromBase64Url(iri.getLocalName).toOption;
			obj <- objFetcher.fetch(hash)
		) yield obj

		case _ =>
			None
	}

	def getDataCiteCitation(dobj: StaticObject): Option[String] = for(
		doiStr <- dobj.doi;
		doi <- Doi.unapply(doiStr);
		cit <- dataCiter.getCitation(doi).value.flatMap(_.toOption)
	) yield cit

	def getIcosCitation(dobj: DataObject): Option[String] = {
		val isIcos: Option[Unit] = if(dobj.specification.project.uri === vocab.icosProject) Some(()) else None

		def titleOpt = dobj.specificInfo.fold(
			l3 => Some(l3.title),
			l2 => for(
					spec <- dobj.specification.self.label;
					acq = l2.acquisition;
					interval <- acq.interval
				) yield {
					val station = acq.station.name
					val height = acq.samplingHeight.fold("")(sh => s" ($sh m)")
					val duration = Duration.between(interval.start, interval.stop)
					val time = if(duration.getSeconds < 24 * 3601){ //daily data object
						val middle = Instant.ofEpochMilli((interval.start.toEpochMilli + interval.stop.toEpochMilli) / 2)
						formatDate(middle)
					} else{
						val from = formatDate(interval.start)
						val to = formatDate(interval.stop)
						s"$from-$to"
					}
					s"$spec, $station$height, $time"
				}
		)

		for(
			_ <- isIcos;
			title <- titleOpt;
			pid <- dobj.doi.orElse(dobj.pid);
			productionInstant <- dobj.production.map(_.dateTime).orElse{
				dobj.specificInfo.toOption.flatMap(_.acquisition.interval).map(_.stop)
			}
		) yield {

//			val station = dobj.specificInfo.fold(
//				_ => None, //L3 data
//				l2 => if(dobj.specification.dataLevel > 1) None else{
//					Some(l2.acquisition.station.name)
//				}
//			).fold("")(_ + ", ")

//			val producerOrg = dobj.production.flatMap(_.creator match{
//				case Organization(_, name) => Some(name)
//				case _ => None
//			}).fold("")(_ + ", ")

//			val icos = if(dobj.specification.dataLevel == 2) "ICOS ERIC, " else ""

			//val authors = s"$icos$producerOrg$station"
			val year = formatDate(productionInstant).take(4)
			s"ICOS RI, $year. $title, ${coreConf.handleService}$pid"
		}
	}

	private def formatDate(inst: Instant): String = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC")).format(inst)

}
