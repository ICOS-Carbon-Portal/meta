package se.lu.nateko.cp.meta.services.metaexport

import se.lu.nateko.cp.doi.CoolDoi
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.doi.DoiMeta
import se.lu.nateko.cp.doi.meta.*
import se.lu.nateko.cp.meta.core.data.Agent
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.DocObject
import se.lu.nateko.cp.meta.core.data.FunderIdType
import se.lu.nateko.cp.meta.core.data.Funding
import se.lu.nateko.cp.meta.core.data.Organization
import se.lu.nateko.cp.meta.core.data.Person
import se.lu.nateko.cp.meta.core.data.PlainStaticObject
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import se.lu.nateko.cp.meta.services.upload.DoiGeoLocationConverter

import java.time.Instant
import java.time.Year
import se.lu.nateko.cp.meta.utils.Validated


class DataCite(doiMaker: String => Doi, fetchCollObjectsRecursively: StaticCollection => Validated[Seq[StaticObject]]):
	import DataCite.{*, given}

	private val ccby4 = Rights("CC BY 4.0", Some("https://creativecommons.org/licenses/by/4.0"))
	private val cc0 = Rights("CC0", Some("https://creativecommons.org/publicdomain/zero/1.0/"))

	def getCreators(obj: StaticObject) = obj.references.authors.fold(Seq.empty[Creator])(_.map(toDoiCreator))

	def getContributors(dobj: DataObject): Seq[Contributor] = dobj.production.toSeq.flatMap{
		_.contributors
			.map(agent => toDoiCreator(agent).toContributor(ContributorType.Producer))
			.distinct
	}

	def getFormat(obj: StaticObject): Option[String] = obj match {
		case dataObj: DataObject =>
			val objFormat = dataObj.specification.format.self
			Some(objFormat.label.getOrElse(objFormat.uri.toString))
		case docObj: DocObject => docObj.fileName.split('.').lastOption.map(_.toUpperCase)
	}

	def makeDataObjectDoi(dobj: DataObject): DoiMeta = {
		val licence: Rights = dobj.references.licence.fold(ccby4)(lic => Rights(lic.name, Some(lic.url.toString)))
		val pubYear = dobj.submission.stop.getOrElse(dobj.submission.start).toString.take(4).toInt

		DoiMeta(
			doi = doiMaker(CoolDoi.makeRandom),
			creators = getCreators(dobj),
			titles = dobj.references.title.map(t => Seq(Title(t, None, None))),
			publisher = Some("ICOS ERIC -- Carbon Portal"),
			publicationYear = Some(pubYear),
			types = Some(ResourceType(None, Some(ResourceTypeGeneral.Dataset))),
			subjects = dobj.keywords.getOrElse(Nil).map(keyword => Subject(keyword)),
			contributors = getContributors(dobj),
			dates = Seq(
				Some(doiDate(dobj.submission.start, DateType.Submitted)),
				tempCoverageDate(dobj),
				dobj.submission.stop.map(s => doiDate(s, DateType.Issued)),
				dobj.production.map(p => doiDate(p.dateTime, DateType.Created))
			).flatten,
			formats = getFormat(dobj).toSeq,
			version = Some(Version(1, 0)),
			rightsList = Some(Seq(licence)),
			descriptions =
				dobj.specificInfo.left.toSeq.flatMap(_.description).map(d => Description(d, DescriptionType.Abstract, None)) ++
				dobj.specification.self.comments.map(comm => Description(comm, DescriptionType.Other, None)),
			geoLocations = dobj.coverage.map(DoiGeoLocationConverter.toDoiGeoLocation),
			fundingReferences = Option(
				CitationMaker.getFundingObjects(dobj).map(toFundingReference)
			).filterNot(_.isEmpty)
		)
	}

	def takeDate(ts: Instant): String = ts.toString.take(10)
	def doiDate(ts: Instant, dtype: DateType) = Date(takeDate(ts), Some(dtype))

	def tempCoverageDate(dobj: DataObject): Option[Date] =
		for
			acq <- dobj.acquisition;
			interval <- acq.interval
		yield
			val dateStr = takeDate(interval.start) + "/" + takeDate(interval.stop)
			Date(dateStr, Some(DateType.Collected))


	def makeDocObjectDoi(doc: DocObject) = DoiMeta(
		doi = doiMaker(CoolDoi.makeRandom),
		titles = doc.references.title.map(title => Seq(Title(title, None, None))),
		publisher = Some("ICOS ERIC -- Carbon Portal"),
		publicationYear = Some(Year.now.getValue),
		descriptions = doc.description.map(d => Description(d, DescriptionType.Abstract, None)).toSeq,
		creators = getCreators(doc),
		dates = Seq(
			Some(doiDate(doc.submission.start, DateType.Submitted)),
			doc.submission.stop.map(s => doiDate(s, DateType.Issued))
		).flatten,
		formats = getFormat(doc).toSeq,
		rightsList = Some(Seq(cc0)),
	)

	def makeCollectionDoi(coll: StaticCollection): Validated[DoiMeta] = fetchCollObjectsRecursively(coll).map: collObjects =>

		val dataObjs = collObjects
			.collect{ case dobj: DataObject => dobj}

		val creators = collObjects
			.flatMap(getCreators)
			.distinct
			.sortBy(_.name)

		val subjects = dataObjs
			.flatMap(_.keywords.getOrElse(Nil))
			.distinct
			.sorted
			.map(keyword => Subject(keyword))

		val funders = dataObjs
			.flatMap(CitationMaker.getFundingObjects)
			.distinct
			.map(toFundingReference)

		val geoLocations = DoiGeoLocationCreator.representativeCoverage(dataObjs.flatMap(_.coverage))

		DoiMeta(
			doi = doiMaker(CoolDoi.makeRandom),
			creators = creators,
			titles = Some(Seq(Title(coll.title, None, None))),
			publisher = Some("ICOS ERIC -- Carbon Portal"),
			publicationYear = Some(Year.now.getValue),
			types = Some(ResourceType(None, Some(ResourceTypeGeneral.Collection))),
			subjects = subjects,
			contributors = dataObjs.flatMap(getContributors).distinct.sortBy(_.name),
			dates = Seq(doiDate(Instant.now, DateType.Issued)),
			formats = collObjects.flatMap(getFormat).distinct,
			version = Some(Version(1, 0)),
			rightsList = Some(Seq(ccby4)),
			descriptions = coll.description.map(d => Description(d, DescriptionType.Abstract, None)).toSeq,
			fundingReferences = Option(funders).filterNot(_.isEmpty),
			geoLocations = Option(geoLocations).filterNot(_.isEmpty)
		)
	end makeCollectionDoi

	def toFundingReference(funding: Funding) = {
		val funderIdentifier = funding.funder.id.flatMap((s, idType) => {
			val scheme = idType match {
				case FunderIdType.`Crossref Funder ID` => FunderIdentifierScheme.Crossref
				case FunderIdType.GRID => FunderIdentifierScheme.Grid
				case FunderIdType.ISNI => FunderIdentifierScheme.Isni
				case FunderIdType.ROR => FunderIdentifierScheme.Ror
				case FunderIdType.Other => FunderIdentifierScheme.Other
			}

			Some(FunderIdentifier(Some(s), Some(scheme)))
		})

		FundingReference(
			Some(funding.funder.org.name), funderIdentifier,
			Some(Award(funding.awardTitle, funding.awardNumber, funding.awardUrl.flatMap(uri => Some(uri.toString))))
		)
	}

	def toDoiCreator(p: Agent) = p match {
		case Organization(_, name, _, _, _) =>
			Creator(
				name = GenericName(name),
				nameIdentifiers = Nil,
				affiliation = Nil
			)
		case Person(_, firstName, lastName, _, orcid) =>
			Creator(
				name = PersonalName(firstName, lastName),
				nameIdentifiers = orcid.map(orc => NameIdentifier(orc.shortId, NameIdentifierScheme.ORCID)).toSeq,
				affiliation = Nil
			)
	}

end DataCite

object DataCite:

	extension (creator: Creator)
		def toContributor(contrType: ContributorType) =
			Contributor(creator.name, creator.nameIdentifiers, creator.affiliation, Some(contrType))

	given nameOrdering: Ordering[Name] = Ordering
		.by[Name, Boolean]{
			case _: PersonalName => false //people first
			case _: GenericName => true   //then orgs
		}
		.orElseBy{
			case PersonalName(_, familyName) => familyName
			case GenericName(name) => name
		}
		.orElseBy{
			case PersonalName(givenName, _) => givenName
			case _ => ""
		}
