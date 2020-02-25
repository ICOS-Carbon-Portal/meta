package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.sail.Sail
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.api.CitationClient
import akka.actor.ActorSystem
import akka.stream.Materializer
import org.eclipse.rdf4j.model.Resource
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.core.data.objectPrefix
import se.lu.nateko.cp.meta.services.upload.CollectionFetcherLite
import se.lu.nateko.cp.meta.services.upload.StaticObjectFetcher
import se.lu.nateko.cp.meta.services.upload.PlainStaticObjectFetcher
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
import se.lu.nateko.cp.meta.core.data.TimeInterval
import org.eclipse.rdf4j.repository.sail.SailRepository
import akka.event.LoggingAdapter
import scala.util.Try
import scala.util.Success
import scala.util.Failure

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
		new CitationProvider(dataCiter, sail, conf.core, conf.dataUploadService, system.log)
	}

}

class CitationProvider(val dataCiter: CitationClient, sail: Sail, coreConf: MetaCoreConfig, uploadConf: UploadServiceConfig, log: LoggingAdapter){
	private implicit val envriConfs = coreConf.envriConfigs
	private val repo = new SailRepository(sail)
	private val attrProvider = new AttributionProvider(repo)
	val vocab = new CpVocab(sail.getValueFactory)

	def getCitation(maybeDobj: Resource): Option[String] = toOption("citation")(Try{
		getStaticObject(maybeDobj).flatMap{
			case (obj, envri) => obj.asDataObject.flatMap{ dobj =>
				if (envri == Envri.SITES) getSitesCitation(dobj)
				else getIcosCitation(dobj)
			}.orElse(getDataCiteCitation(obj))
		}
	}).flatten

	private val objFetcher = {
		val pidFactory = new HandleNetClient.PidFactory(uploadConf.handle)
		val server = new Rdf4jInstanceServer(repo)

		val collFetcher = new CollectionFetcherLite(server, vocab)
		val plainFetcher = new PlainStaticObjectFetcher(server)
		new StaticObjectFetcher(server, vocab, collFetcher, plainFetcher, pidFactory)
	}

	def getStaticObject(maybeDobj: Resource): Option[(StaticObject, Envri)] = maybeDobj match {

		case iri: IRI => for(
			envri <- inferObjectEnvri(iri);
			hash <- toOption("hashsum")(Sha256Sum.fromBase64Url(iri.getLocalName));
			obj <- objFetcher.fetch(hash)(envri)
		) yield obj -> envri

		case _ =>
			None
	}

	private def inferObjectEnvri(obj: IRI): Option[Envri] = Envri.infer(obj.toJava).filter{
		envri => obj.stringValue.startsWith(objectPrefix(envriConfs(envri)))
	}

	def getDataCiteCitation(dobj: StaticObject): Option[String] = for(
		doiStr <- dobj.doi;
		doi <- Doi.unapply(doiStr);
		cit <- dataCiter.getCitation(doi).value //not waiting for the future
			.flatMap(toOption("DataCite citation"))
	) yield cit

	def getIcosCitation(dobj: DataObject): Option[String] = {
		val isIcos: Option[Unit] = if(dobj.specification.project.uri === vocab.icosProject) Some(()) else None
		val zoneId = ZoneId.of("UTC")

		def titleOpt = dobj.specificInfo.fold(
			l3 => Some(l3.title),
			l2 => for(
					spec <- dobj.specification.self.label;
					acq = l2.acquisition;
					interval <- acq.interval
				) yield {
					val station = acq.station.name
					val height = acq.samplingHeight.fold("")(sh => s" ($sh m)")
					val time = getTimeFromInterval(interval, zoneId)
					s"$spec, $station$height, $time"
				}
		)
		for(
			_ <- isIcos;
			title <- titleOpt;
			pid <- dobj.doi.orElse(dobj.pid);
			productionInstant <- AttributionProvider.productionTime(dobj)
		) yield {
			val authors = attrProvider.getAuthors(dobj).map{p => s"${p.lastName}, ${p.firstName.head}., "}.mkString
			val handleProxy = if(dobj.doi.isDefined) coreConf.handleProxies.doi else coreConf.handleProxies.basic
			val year = formatDate(productionInstant, zoneId).take(4)
			s"${authors}ICOS RI, $year. $title, ${handleProxy}$pid"
		}
	}

	private def formatDate(inst: Instant, zoneId: ZoneId): String = DateTimeFormatter.ISO_LOCAL_DATE.withZone(zoneId).format(inst)

	def getSitesCitation(dobj: DataObject): Option[String] = {
		val zoneId = ZoneId.of("UTC+01:00")
		val titleOpt = dobj.specificInfo.fold(
			l3 => Some(l3.title),
			l2 => for(
					spec <- dobj.specification.self.label;
					acq = l2.acquisition;
					location <- acq.site.flatMap(_.self.label);
					interval <- acq.interval;
					productionInstant = dobj.production.fold(interval.stop)(_.dateTime)
				) yield {
					val station = acq.station.name
					val time = getTimeFromInterval(interval, zoneId)
					val year = formatDate(productionInstant, zoneId).take(4)
					val dataType = spec.split(",").head
					s"$station. $year. $dataType from $location, $time"
				}
		)

		for(
			title <- titleOpt;
			handleProxy = if(dobj.doi.isDefined) coreConf.handleProxies.doi else coreConf.handleProxies.basic;
			pid <- dobj.doi.orElse(dobj.pid)
		) yield {
			s"$title. SITES Data Portal. ${handleProxy}$pid"
		}
	}

	private def getTimeFromInterval(interval: TimeInterval, zoneId: ZoneId): String = {
		val duration = Duration.between(interval.start, interval.stop)
		if (duration.getSeconds < 24 * 3601) { //daily data object
			val middle = Instant.ofEpochMilli((interval.start.toEpochMilli + interval.stop.toEpochMilli) / 2)
			formatDate(middle, zoneId)
		} else {
			val from = formatDate(interval.start, zoneId)
			val to = formatDate(interval.stop, zoneId)
			s"$from–$to"
		}
	}

	private def toOption[T](hint: String)(tr: Try[T]): Option[T] = tr match {
		case Success(t) => Some(t)
		case Failure(exc) =>
			log.error(exc, s"Error while obtaining $hint")
			None
	}

}
