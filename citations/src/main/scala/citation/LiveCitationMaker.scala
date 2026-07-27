package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.{RDFS, SKOS}
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.doi.{Doi, DoiMeta}
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.utils.{Validated, parseCommaSepList}

import java.time.ZoneId
import scala.util.{Failure, Success, Try}

private class CitationInfo(
	val pidUrl: Option[String],
	val authors: Option[Seq[Agent]],
	val title: Option[String],
	val year: Option[String],
	val tempCovDisplay: Option[String],
	val citText: Option[String],
)

/**
 * The DataCite-backed citation assembly that used to be `class CitationMaker` in
 * the meta service. It computes the full [[References]] for an object — citation
 * strings (incl. DOI citations fetched via [[PlainDoiCiter]]), authors, title,
 * licence — and now lives in the standalone citations service, which both
 * materializes these references into the triplestore and serves freshly-computed
 * objects to the meta service's DOI-minting path.
 *
 * The pure structural helpers remain in meta's `object CitationMaker`.
 */
class LiveCitationMaker(
	doiCiter: PlainDoiCiter,
	vocab: CpVocab,
	metaVocab: CpmetaVocab,
	coreConf: MetaCoreConfig
) extends CitationInfoProvider {
	private val log = LoggerFactory.getLogger(getClass())
	import CitationMaker.*
	import Validated.getOrElseV
	import StatementSource.*
	import RdfLens.{DobjConn, DocConn, GlobConn, MetaConn}
	private given envriConfs: EnvriConfigs = coreConf.envriConfigs

	private def defaultTimezoneId(using envri: Envri): String = envriConfs(envri).defaultTimezoneId

	val attrProvider = new AttributionProvider(vocab, metaVocab)

	def getItemCitationInfo(item: CitableItem, itemIri: IRI)(using GlobConn): References = item.references.copy(
		citationString = getDoiCitation(item, CitationStyle.HTML),
		citationBibTex = getDoiCitation(item, CitationStyle.bibtex),
		citationRis    = getDoiCitation(item, CitationStyle.ris),
		doi = getDoiMeta(item)
	)

	def getCitationInfo(sobj: StaticObject)(using Envri, DocConn | DobjConn): Validated[References] =
		getCitationInfo(sobj, includeDataCite = true)

	/** The same canonical citation construction without touching DataCite. Used by
	 *  bulk materialization to prepare a queue job exactly once. */
	val structural: CitationInfoProvider = new CitationInfoProvider {
		def getCitationInfo(sobj: StaticObject)(using Envri, DocConn | DobjConn): Validated[References] =
			LiveCitationMaker.this.getCitationInfo(sobj, includeDataCite = false)

		def getItemCitationInfo(item: CitableItem, itemIri: IRI)(using GlobConn): References =
			item.references.copy(citationString = None, citationBibTex = None, citationRis = None, doi = None)
	}

	private def getCitationInfo(
		sobj: StaticObject, includeDataCite: Boolean
	)(using Envri, DocConn | DobjConn): Validated[References] =
		for {
			citInfo <- sobj match {
				case doc:  DocObject  => Validated(getDocCitation(doc))
				case dobj: DataObject => summon[Envri] match {
					case Envri.SITES => Validated(getSitesCitation(dobj))
					case Envri.ICOS | Envri.ICOSCities => getIcosCitation(dobj)
				}
			}
			dobj = vocab.getStaticObject(sobj.hash)
			keywordsS <- getOptionalString(dobj, metaVocab.hasKeywords)
			theLicence <- getLicence(dobj)
		}
		yield {
			val keywords = keywordsS.map(s => parseCommaSepList(s).toIndexedSeq)
			val structuredCitations = new StructuredCitations(sobj, citInfo, keywords, theLicence)

			val dataCiteCitation = Option.when(includeDataCite)(getDoiCitation(sobj, CitationStyle.HTML)).flatten
			val dataCiteBibTex = Option.when(includeDataCite)(getDoiCitation(sobj, CitationStyle.bibtex)).flatten
			val dataCiteRis = Option.when(includeDataCite)(getDoiCitation(sobj, CitationStyle.ris)).flatten
			val doiMeta = Option.when(includeDataCite)(getDoiMeta(sobj)).flatten
			val coreRefs = sobj.references.copy(
				citationString = dataCiteCitation.orElse(citInfo.citText),
				citationBibTex = dataCiteBibTex.orElse(Some(structuredCitations.toBibTex)),
				citationRis = dataCiteRis.orElse(Some(structuredCitations.toRis)),
				doi = doiMeta,
				authors = citInfo.authors,
				title = citInfo.title,
				licence = Some(theLicence),
				keywords = keywords
			)

			sobj match {
				case data: DataObject => coreRefs.copy(
					temporalCoverageDisplay = citInfo.tempCovDisplay,
					acknowledgements = Option(getFundingAcknowledgements(data)).filter(_.nonEmpty),
				)
				case _: DocObject => coreRefs
			}
		}

	def getLicence(dobj: IRI)(using Envri, DobjConn | DocConn): Validated[Licence] = {

		def getLic(licUri: IRI): Validated[Licence] = for {
			name <- getSingleString(licUri, RDFS.LABEL)
			webpageOpt <- getOptionalUri(licUri, RDFS.SEEALSO)
			baseLicence <- getOptionalUri(licUri, SKOS.EXACT_MATCH)
		}
		yield {
			val webpage = webpageOpt.getOrElse(licUri).toJava
			Licence(licUri.toJava, name, webpage, baseLicence.map(_.toJava))
		}

		def getOptLic(res: IRI, licPred: IRI): Validated[Option[Licence]] =
			for {
				optLicUri <- getOptionalUri(res, licPred)
				optLic <- Validated.sinkOption(optLicUri.map(getLic))
			}
			yield optLic

		inline def getImpliedLic(term: IRI) = getOptLic(term, metaVocab.impliesDefaultLicence)

		for {
			ownLicOpt <- getOptLic(dobj, metaVocab.dcterms.license)
			lic <- ownLicOpt.getOrElseV {
				for {
					specIriOpt <- getOptionalUri(dobj, metaVocab.hasObjectSpec)(using RdfLens.global)
					lic <- specIriOpt match {
						case None => Validated.ok(defaultLicence) //not a data object
						case Some(specIri) =>
							for {
								specLicOpt <- getImpliedLic(specIri)
								lic <- specLicOpt.getOrElseV {
									for {
										projIri <- getSingleUri(specIri, metaVocab.hasAssociatedProject)
										projLicOpt <- getImpliedLic(projIri)
									}
									yield
										projLicOpt.getOrElse(defaultLicence)
								}
							}
							yield lic
					}
				}
				yield lic
			}
		}
		yield lic
	} // end getLicence

	def presentDoiCitation(eagerRes: Option[Try[String]]): String = eagerRes match{
		case None => "Fetching... try refreshing the page in a few seconds"
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
		for {
			doiStr <- item.doi;
			doi <- Doi.parse(doiStr).toOption;
			doiMeta <- doiCiter.getDoiEager(doi) match {
				case None => Some(DoiMeta(doi))
				case Some(Success(doiMeta)) => Some(doiMeta)
				case Some(Failure(err)) =>
					log.error("Error fetching DOI citation", err)
					None
			}
		}
		yield doiMeta

	private def getIcosCitation(dobj: DataObject)(using Envri, MetaConn): Validated[CitationInfo] = {
		val zoneId = ZoneId.of(defaultTimezoneId)
		val tempCov = getTemporalCoverageDisplay(dobj, zoneId)
		val isIcosProject = dobj.specification.project.self.uri === vocab.icosProject
		val isMiscProject = dobj.specification.project.self.uri === vocab.miscProject
		val isIcosLikeStationMeas = dobj.specificInfo.fold(
			_ => false,
			_.acquisition.station.specificInfo match {
				case _:IcosStationSpecifics => true
				case _ => false
			}
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

		val authorsV: Validated[Seq[Agent]] = {

			lazy val productionAgents = dobj.production.toSeq.flatMap { prod =>
				if prod.contributors.contains(prod.creator) then prod.contributors
				else prod.creator +: prod.contributors
			}

			if isIcosLikeStationMeas && dobj.specification.dataLevel < 3 then
				attrProvider.getAuthors(dobj).map { attrAuthors =>
					if isIcosProject then attrAuthors
					else {
						val hasProdPerson = productionAgents.exists {
							case _: Person => true
							case _ => false
						}
						if hasProdPerson then productionAgents else attrAuthors
					}
				}
			else Validated.ok(productionAgents)
		}

		val pidUrlOpt = getPidUrl(dobj)
		val projectOpt =
			if isIcosProject then Some("ICOS RI")
			else if isMiscProject then None
			else dobj.specification.project.self.label
		val yearOpt = productionTime(dobj).map(getYear(zoneId))

		val project = if projectOpt.nonEmpty then projectOpt.mkString("", "", ",") else ""

		authorsV.map { authors =>
			val citText = for(
				title <- titleOpt;
				pidUrl <- pidUrlOpt;
				time <- tempCov;
				year <- yearOpt
			) yield {
				val authorsStr = authors.map{
					case p: Person => s"${p.lastName}, ${p.firstName.head}."
					case o: Organization => o.name
				}.mkString(", ")
				s"${authorsStr} ($year). $title, $time, $project $pidUrl"
			}

			new CitationInfo(pidUrlOpt, Option(authors).filterNot(_.isEmpty), titleOpt, yearOpt, tempCov, citText)
		}
	} // end getIcosCitation

	private def getSitesCitation(dobj: DataObject)(using e: Envri): CitationInfo = {
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

	} // end getSitesCitation

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

	private def getDocCitation(doc: DocObject)(using envri: Envri): CitationInfo = {
		import doc.{references => refs}
		val zoneId = ZoneId.of(defaultTimezoneId)
		val yearOpt = doc.submission.stop.map(getYear(zoneId))
		val authors: Seq[String] = refs.authors.fold(Seq())(_.distinct.collect{
			case p: Person => s"${p.lastName}, ${p.firstName.head}."
			case o: Organization => o.name
		})

		val authorString = if (authors.nonEmpty) Some(authors.mkString(", ")) else None

		val pidUrlOpt = getPidUrl(doc)
		val citString = for {
			year <- yearOpt
			title <- refs.title.orElse(Some(doc.fileName))
			pidUrl <- pidUrlOpt
		}
		yield envri match {
			case Envri.SITES =>
				s"${authorString.getOrElse(envri.shortName)} ($year). $title. ${envri.longName} (${envri.shortName}). $pidUrl"
			case Envri.ICOS | Envri.ICOSCities =>
				s"${authorString.getOrElse("ICOS RI")}, $year. $title, $pidUrl"
		}

		CitationInfo(pidUrlOpt, refs.authors, refs.title, yearOpt, None, citString)
	}

} // end LiveCitationMaker
