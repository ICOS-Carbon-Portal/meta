package se.lu.nateko.cp.meta.services.citation

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.{RDFS, SKOS}
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.doi.{Doi, DoiMeta}
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.metaflow.icos.EtcMetaSource.toCETnoon
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.utils.{Validated, parseCommaSepList}

import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant, ZoneId, ZonedDateTime}
import scala.util.{Failure, Success, Try}

private class CitationInfo(
	val pidUrl: Option[String],
	val authors: Option[Seq[Agent]],
	val title: Option[String],
	val year: Option[String],
	val tempCovDisplay: Option[String],
	val citText: Option[String],
)

class CitationMaker(
	doiCiter: PlainDoiCiter,
	vocab: CpVocab,
	metaVocab: CpmetaVocab,
	coreConf: MetaCoreConfig
):
	private val log = LoggerFactory.getLogger(getClass())
	import CitationMaker.*
	import Validated.getOrElseV
	import StatementSource.*
	import RdfLens.{DobjConn, DocConn, MetaConn}
	private given envriConfs: EnvriConfigs = coreConf.envriConfigs

	private def defaultTimezoneId(using envri: Envri): String = envriConfs(envri).defaultTimezoneId

	val attrProvider = new AttributionProvider(vocab, metaVocab)

	def getItemCitationInfo(item: CitableItem): References = item.references.copy(
		citationString = getDoiCitation(item, CitationStyle.HTML),
		citationBibTex = getDoiCitation(item, CitationStyle.bibtex),
		citationRis    = getDoiCitation(item, CitationStyle.ris),
		doi = getDoiMeta(item)
	)

	def getCitationInfo(sobj: StaticObject)(using Envri, DocConn | DobjConn): Validated[References] =
		for
			citInfo <- sobj match
				case doc:  DocObject  => Validated(getDocCitation(doc))
				case dobj: DataObject => summon[Envri] match
					case Envri.SITES => Validated(getSitesCitation(dobj))
					case Envri.ICOS | Envri.ICOSCities => getIcosCitation(dobj)
			dobj = vocab.getStaticObject(sobj.hash)
			keywordsS <- getOptionalString(dobj, metaVocab.hasKeywords)
			theLicence <- getLicence(dobj)
		yield
			val keywords = keywordsS.map(s => parseCommaSepList(s).toIndexedSeq)
			val structuredCitations = new StructuredCitations(sobj, citInfo, keywords, theLicence)

			val coreRefs = sobj.references.copy(
				citationString = getDoiCitation(sobj, CitationStyle.HTML).orElse(citInfo.citText),
				citationBibTex = getDoiCitation(sobj, CitationStyle.bibtex).orElse(Some(structuredCitations.toBibTex)),
				citationRis = getDoiCitation(sobj, CitationStyle.ris).orElse(Some(structuredCitations.toRis)),
				doi = getDoiMeta(sobj),
				authors = citInfo.authors,
				title = citInfo.title,
				licence = Some(theLicence),
				keywords = keywords
			)

			sobj match
				case data: DataObject => coreRefs.copy(
					temporalCoverageDisplay = citInfo.tempCovDisplay,
					acknowledgements = Option(getFundingAcknowledgements(data)).filter(_.nonEmpty),
				)
				case _: DocObject => coreRefs

	end getCitationInfo


	def getLicence(dobj: IRI)(using Envri, DobjConn | DocConn): Validated[Licence] =

		def getLic(licUri: IRI): Validated[Licence] = for
			name <- getSingleString(licUri, RDFS.LABEL)
			webpageOpt <- getOptionalUri(licUri, RDFS.SEEALSO)
			baseLicence <- getOptionalUri(licUri, SKOS.EXACT_MATCH)
		yield
			val webpage = webpageOpt.getOrElse(licUri).toJava
			Licence(licUri.toJava, name, webpage, baseLicence.map(_.toJava))

		def getOptLic(res: IRI, licPred: IRI): Validated[Option[Licence]] =
			for
				optLicUri <- getOptionalUri(res, licPred)
				optLic <- Validated.sinkOption(optLicUri.map(getLic))
			yield optLic

		inline def getImpliedLic(term: IRI) = getOptLic(term, metaVocab.impliesDefaultLicence)

		for
			ownLicOpt <- getOptLic(dobj, metaVocab.dcterms.license)
			lic <- ownLicOpt.getOrElseV:
				for
					specIriOpt <- getOptionalUri(dobj, metaVocab.hasObjectSpec)(using RdfLens.global)
					lic <- specIriOpt match
						case None => Validated.ok(defaultLicence) //not a data object
						case Some(specIri) =>
							for
								specLicOpt <- getImpliedLic(specIri)
								lic <- specLicOpt.getOrElseV:
									for
										projIri <- getSingleUri(specIri, metaVocab.hasAssociatedProject)
										projLicOpt <- getImpliedLic(projIri)
									yield
										projLicOpt.getOrElse(defaultLicence)
							yield lic
				yield lic
		yield lic
	end getLicence

	def presentDoiCitation(eagerRes: Option[Try[String]]): String = eagerRes match{
		case None => "Fetching... try [refreshing the page] again in a few seconds"
		case Some(Success(cit)) => cit
		case Some(Failure(err)) => "Error fetching DOI citation: " + err.getMessage
	}

	def extractDoiCitation(style: CitationStyle): PartialFunction[String, String] =
		Function.unlift((s: String) => Doi.parse(s).toOption).andThen(
			doi => presentDoiCitation(doiCiter.getCitationEager(doi, style))
		)

	private def getDoiCitation(item: CitableItem, style: CitationStyle): Option[String] =
		item.doi.collect{ extractDoiCitation(style) }

	private def getDoiMeta(item: CitableItem): Option[DoiMeta] =
		for
			doiStr <- item.doi;
			doi <- Doi.parse(doiStr).toOption;
			doiMeta <- doiCiter.getDoiEager(doi) match
				case None => Some(DoiMeta(doi))
				case Some(Success(doiMeta)) => Some(doiMeta)
				case Some(Failure(err)) =>
					log.error("Error fetching DOI citation", err)
					None
		yield doiMeta

	private def getIcosCitation(dobj: DataObject)(using Envri, MetaConn): Validated[CitationInfo] =
		val zoneId = ZoneId.of(defaultTimezoneId)
		val tempCov = getTemporalCoverageDisplay(dobj, zoneId)
		val isIcosProject = dobj.specification.project.self.uri === vocab.icosProject
		val isIcosLikeStationMeas = dobj.specificInfo.fold(
			_ => false,
			_.acquisition.station.specificInfo match
				case _:IcosStationSpecifics => true
				case _ => false
		)

		def titleOpt = dobj.specificInfo.fold(
			spatioTemp => Some(spatioTemp.title),
			stationTs => for(
					spec <- dobj.specification.self.label;
					acq = stationTs.acquisition
				) yield {
					val station = acq.station.org.name
					val height = acq.samplingHeight.fold("")(sh => s" ($sh m)")
					val vars =
						if dobj.specification.self.uri === vocab.atmGhgProdSpec then
							stationTs.columns.fold("")(_.collect{
								case v if v.valueType.unit.isDefined => v.label
							}.mkString(" (", ", ", ")"))
						else ""

					s"$spec$vars from $station$height"
				}
		)

		val authorsV: Validated[Seq[Agent]] =

			lazy val productionAgents = dobj.production.toSeq.flatMap: prod =>
				if prod.contributors.contains(prod.creator) then prod.contributors
				else prod.creator +: prod.contributors

			if isIcosLikeStationMeas && dobj.specification.dataLevel < 3 then
				attrProvider.getAuthors(dobj).map: attrAuthors =>
					if isIcosProject then attrAuthors
					else
						val hasProdPerson = productionAgents.exists:
							case _: Person => true
							case _ => false
						if hasProdPerson then productionAgents else attrAuthors
			else Validated.ok(productionAgents)

		val pidUrlOpt = getPidUrl(dobj)
		val projName = if(isIcosProject) Some("ICOS RI") else dobj.specification.project.self.label
		val yearOpt = productionTime(dobj).map(getYear(zoneId))

		authorsV.map: authors =>
			val citText = for(
				title <- titleOpt;
				pidUrl <- pidUrlOpt;
				time <- tempCov;
				year <- yearOpt;
				projName <- projName
			) yield {
				val authorsStr = authors.map{
					case p: Person => s"${p.lastName}, ${p.firstName.head}."
					case o: Organization => o.name
				}.mkString(", ")
				s"${authorsStr} ($year). $title, $time, $projName, $pidUrl"
			}

			new CitationInfo(pidUrlOpt, Option(authors).filterNot(_.isEmpty), titleOpt, yearOpt, tempCov, citText)
	end getIcosCitation

	private def getSitesCitation(dobj: DataObject)(using e: Envri): CitationInfo =
		val zoneId = ZoneId.of(defaultTimezoneId)
		val tempCov = getTemporalCoverageDisplay(dobj, zoneId)
		val yearOpt = dobj.submission.stop.map(getYear(zoneId))

		val titleOpt = dobj.specificInfo.fold(
			spatioTemp => Some(spatioTemp.title),
			stationTs => for(
				spec <- dobj.specification.self.label;
				acq = stationTs.acquisition;
				location <- acq.site.flatMap(_.location.flatMap(_.label))
			) yield {
				val dataType = spec.split(",").head
				val samplingPoint = acq.samplingPoint.flatMap(_.label)
				s"$dataType from ${samplingPoint.getOrElse(location)}"
			}
		)

		val authors = dobj.specificInfo.fold(
			_ => "",
			stationTs => s"${stationTs.acquisition.station.org.name} "
		)
		val pidUrlOpt = getPidUrl(dobj)
		val citString = for(
			year <- yearOpt;
			title <- titleOpt;
			time <- tempCov;
			pidUrl <- pidUrlOpt
		) yield s"$authors($year). $title, $time [Data set]. ${e.longName} (${e.shortName}). $pidUrl"

		new CitationInfo(pidUrlOpt, None, titleOpt, yearOpt, tempCov, citString)

	end getSitesCitation

	private def getPidUrl(dobj: StaticObject): Option[String] = for(
		pid <- dobj.doi.orElse(dobj.pid);
		handleProxy = if(dobj.doi.isDefined) coreConf.handleProxies.doi else coreConf.handleProxies.basic
	) yield s"$handleProxy$pid"

	private def getFundingAcknowledgements(dobj: DataObject): Seq[String] = getFundingObjects(dobj).map{
		funding =>
			val grantTitle = List(funding.awardTitle, funding.awardNumber).flatten match{
				case only :: Nil => s" $only"
				case title :: number :: Nil => s" $title ($number)"
				case _ => ""
			}
			s"Work was funded by grant$grantTitle from ${funding.funder.org.name}"
		}

	private def getDocCitation(doc: DocObject)(using envri: Envri): CitationInfo =
		import doc.{references => refs}
		val zoneId = ZoneId.of(defaultTimezoneId)
		val yearOpt = doc.submission.stop.map(getYear(zoneId))
		val authorString = refs.authors.fold("")(_.distinct.collect{
			case p: Person => s"${p.lastName}, ${p.firstName.head}."
			case o: Organization => o.name
		}.mkString("", ", ", " "))

		val pidUrlOpt = getPidUrl(doc)
		val citString = for
			year <- yearOpt
			title <- refs.title
			pidUrl <- pidUrlOpt
		yield envri match
			case Envri.SITES =>
				s"${authorString}($year). $title. ${envri.longName} (${envri.shortName}). $pidUrl"
			case Envri.ICOS | Envri.ICOSCities =>
				s"${authorString}ICOS RI, $year. $title, $pidUrl"

		CitationInfo(pidUrlOpt, refs.authors, refs.title, yearOpt, None, citString)

end CitationMaker

object CitationMaker:

	def defaultLicence(using envri: Envri): Licence = envri match
		case Envri.ICOS | Envri.ICOSCities => Licence(
			new URI(CpmetaVocab.MetaPrefix + "icosLicence"),
			"ICOS CCBY4 Data Licence",
			new URI("https://data.icos-cp.eu/licence"),
			Some(CpVocab.CCBY4)
		)
		case Envri.SITES => Licence(
			new URI(CpmetaVocab.SitesPrefix + "sitesLicence"),
			"SITES CCBY4 Data Licence",
			new URI("https://data.fieldsites.se/licence"),
			Some(CpVocab.CCBY4)
		)


	def getFundingObjects(dobj: DataObject): Seq[Funding] =  dobj.specificInfo match
		case Right(stationTs) =>
			val acq = stationTs.acquisition
			acq.station.funding.toSeq.flatten.filter{funding =>
				funding.start.fold(true){
					fstart => acq.interval.fold(true)(_.stop.compareTo(toCETnoon(fstart)) > 0)
				} &&
				funding.stop.fold(true){
					fstop => acq.interval.fold(true)(_.start.compareTo(toCETnoon(fstop)) < 0)
				}
			}
		case _ => Seq.empty

	def getTemporalCoverageDisplay(dobj: DataObject, zoneId: ZoneId): Option[String] = dobj.specificInfo.fold(
		spatioTemp => Some(getTimeFromInterval(spatioTemp.temporal.interval, zoneId)),
		stationTs => stationTs.acquisition.interval.map(getTimeFromInterval(_, zoneId))
	)

	private def getTimeFromInterval(interval: TimeInterval, zoneId: ZoneId): String = {
		val duration = Duration.between(interval.start, interval.stop)
		val startZonedDateTime = ZonedDateTime.ofInstant(interval.start, zoneId)
		val stopZonedDateTime = ZonedDateTime.ofInstant(interval.stop, zoneId)
		if (duration.getSeconds < 24 * 3601) { //daily data object
			val middle = Instant.ofEpochMilli((interval.start.toEpochMilli + interval.stop.toEpochMilli) / 2)
			formatDate(middle, zoneId)
		} else if (startZonedDateTime.getDayOfYear == 1 && stopZonedDateTime.getDayOfYear == 1) {
			if (startZonedDateTime.getYear == stopZonedDateTime.getYear - 1) {
				s"${startZonedDateTime.getYear}"
			} else {
				s"${startZonedDateTime.getYear}–${stopZonedDateTime.getYear - 1}"
			}
		} else if (isMidnight(startZonedDateTime) && isMidnight(stopZonedDateTime)) {
			val from = formatDate(interval.start, zoneId)
			val to = formatDate(interval.stop.minus(1, ChronoUnit.DAYS), zoneId)
			s"$from–$to"
		} else {
			val from = formatDate(interval.start, zoneId)
			val to = formatDate(interval.stop, zoneId)
			s"$from–$to"
		}
	}

	private def getYear(zoneId: ZoneId)(prodInst: Instant): String = formatDate(prodInst, zoneId).take(4)

	private def formatDate(inst: Instant, zoneId: ZoneId): String = DateTimeFormatter.ISO_LOCAL_DATE.withZone(zoneId).format(inst)

	private def productionTime(dobj: DataObject): Option[Instant] =
		dobj.production.map(_.dateTime).orElse{
			dobj.specificInfo.toOption.flatMap(_.acquisition.interval).map(_.stop)
		}

	private def isMidnight(dateTime: ZonedDateTime): Boolean = dateTime.format(DateTimeFormatter.ISO_LOCAL_TIME) == "00:00:00"

end CitationMaker
