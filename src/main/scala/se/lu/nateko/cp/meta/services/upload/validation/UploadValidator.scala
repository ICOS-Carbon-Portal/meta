package se.lu.nateko.cp.meta.services.upload.validation

import akka.NotUsed
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.ConfigLoader
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.DataProductionDto
import se.lu.nateko.cp.meta.DataSubmitterConfig
import se.lu.nateko.cp.meta.DocObjectDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.StationTimeSeriesDto
import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.DataObjectSpec
import se.lu.nateko.cp.meta.core.data.DatasetType
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.core.data.flattenToSeq
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer.Hash
import se.lu.nateko.cp.meta.utils.*
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import java.time.Instant
import java.util.Date
import scala.collection.mutable.Buffer
import scala.language.strictEquality
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.upload.DataObjectInstanceServers
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.api.UriId

given CanEqual[URI, URI] = CanEqual.derived
given CanEqual[IRI, IRI] = CanEqual.derived
given CanEqual[Instant, Instant] = CanEqual.derived

val ok: Try[NotUsed] = Success(NotUsed)
def bothOk(try1: Try[NotUsed], try2: => Try[NotUsed]): Try[NotUsed] = try1.flatMap(_ => try2)
def userFail(msg: String) = Failure(new UploadUserErrorException(msg))
def authFail(msg: String) = Failure(new UnauthorizedUploadException(msg))

class UploadValidator(servers: DataObjectInstanceServers):
	import servers.{ metaVocab, vocab, metaReader, lenses }
	import TriplestoreConnection.*
	import RdfLens.{MetaConn, DobjConn, DocConn, CollConn, ItemConn, GlobConn}
	given vf: ValueFactory = metaVocab.factory
	private val scoped = ScopedValidator(vocab, metaVocab)

	def validateObject(meta: ObjectUploadDto, uploader: UserId)(using Envri): Try[ObjectUploadDto] =
		servers.vanillaGlobal.access:
			for
				given DocConn <- lenses.documentLens.toTry(new MetadataException(_))
				dto <- meta match
					case dobj: DataObjectDto => validateDobj(dobj, uploader)
					case doc: DocObjectDto => validateDoc(doc, uploader)
			yield dto


	private def validateDobj(meta: DataObjectDto, uploader: UserId)(using Envri, DocConn): Try[DataObjectDto] =
		for
			submConf <- getSubmitterConfig(meta);
			_ <- userAuthorizedBySubmitter(submConf, uploader);
			spec <- {
					for
						_ <- userAuthorizedByProducer(meta, submConf)
						spec <- metaReader.getSpecification(meta.objectSpecification.toRdf)
						_ <- validateForFormat(meta, spec, submConf)
					yield spec
			}.toTry(new UploadUserErrorException(_))
			_ <- userAuthorizedByThemesAndProjects(spec, submConf);
			_ <- validateDescription(meta.specificInfo.fold(_.description, _.production.flatMap(_.comment)))
			given DobjConn <- lenses.dataObjectLens(spec.format.self.uri).toTry(new MetadataException(_))
			amended <- for
				_ <- scoped.validatePrevVers(meta)
				_ <- scoped.validateLicence(meta)
				_ <- scoped.growingIsGrowing(meta, spec, submConf)
				_ <- validateActors(meta)
				_ <- validateTemporalCoverage(meta, spec)
				_ <- validateSpatialCoverage(meta)
				_ <- noProductionProvenanceIfL0(meta, spec)
				_ <- validateFormatsByFileExt(meta, spec)
				amended <- scoped.validateFileName(meta)
				_ <- scoped.validateMoratorium(amended)
			yield amended
		yield amended

	private def validateDoc(meta: DocObjectDto, uploader: UserId)(using Envri, DocConn): Try[DocObjectDto] =
		for
			_ <- validateDescription(meta.description)
			submConf <- getSubmitterConfig(meta)
			_ <- userAuthorizedBySubmitter(submConf, uploader)
			instServer <- servers.docServer.toTry(new MetadataException(_))
			amended <- instServer.access:
				for
					_ <- scoped.validatePrevVers(meta)
					_ <- scoped.validateLicence(meta)
					amended <- scoped.validateFileName(meta)
				yield amended
		yield amended


	def validateCollection(coll: StaticCollectionDto, hash: Sha256Sum, uploader: UserId)(using Envri, GlobConn): Try[NotUsed] =
		for
			_ <- collMemberListOk(coll, hash)
			submConf <- getSubmitterConfig(coll)
			_ <- validateDescription(coll.description)
			_ <- userAuthorizedBySubmitter(submConf, uploader)
			_ <- submitterAuthorizedByCollectionCreator(submConf, hash)
			_ <- validatePreviousCollectionVersion(coll.isNextVersionOf, hash)
		yield NotUsed

	def noProductionProvenanceIfL0(meta: DataObjectDto, spec: DataObjectSpec): Try[NotUsed] =
		if spec.dataLevel == 0 && meta.specificInfo.fold(
			spTempMeta => true,
			stationMeta => stationMeta.production.nonEmpty
		) then userFail("Level 0 data object cannot contain production provenance") else ok

	def getSubmitterConfig(dto: UploadDto)(using envri: Envri): Try[DataSubmitterConfig] =
		ConfigLoader.submittersConfig.submitters(envri).get(dto.submitterId) match
			case None => userFail(s"Unknown submitter: ${dto.submitterId}")
			case Some(conf) => Success(conf)


	def updateValidIfObjectNotNew(dto: ObjectUploadDto, submittingOrg: URI)(using Envri, GlobConn): Try[NotUsed] =
		val objIri = vocab.getStaticObject(dto.hashSum)
		for
			_ <- objectKindSameIfNotNew(dto, objIri)
			_ <- submitterIsSameIfObjNotNew(objIri, submittingOrg)
			res <- dto match
				case dobj: DataObjectDto => formatIsSameIfObjectNotNew(dobj, objIri, submittingOrg)
				case _ => ok
		yield res

	private def objectKindSameIfNotNew(dto: ObjectUploadDto, objIri: IRI)(using GlobConn): Try[NotUsed] = dto match
		case _: DataObjectDto =>
			if resourceHasType(objIri, metaVocab.docObjectClass)
			then userFail("Cannot accept data object upload as there is already a document object with id " + dto.hashSum.id)
			else Success(NotUsed)
		case _: DocObjectDto =>
			if resourceHasType(objIri, metaVocab.dataObjectClass)
			then userFail("Cannot accept document object upload as there is already a data object with id " + dto.hashSum.id)
			else Success(NotUsed)


	private def formatIsSameIfObjectNotNew(meta: DataObjectDto, objIri: IRI, submittingOrg: URI)(using GlobConn): Try[NotUsed] =
		def specFormat(spec: IRI): Try[IRI] = metaReader.getObjSpecFormat(spec).toTry(new MetadataException(_))

		getSingleUri(objIri, metaVocab.hasObjectSpec).result.fold(ok/* object is new */): oldSpec =>
			//object is not new

			val newSpec = meta.objectSpecification.toRdf
			if oldSpec === newSpec then ok else
				for
					newFormat <- specFormat(newSpec)
					oldFormat <- specFormat(oldSpec)
					res <- if oldFormat === newFormat then ok else authFail:
						s"Object exists and has format $oldFormat. Upload with format $newFormat is therefore impossible."
				yield res


	private def submitterIsSameIfObjNotNew(objIri: IRI, submittingOrg: URI)(using GlobConn): Try[NotUsed] =
		metaReader.getObjSubmitter(objIri).result.fold(ok): subm =>
			if subm === submittingOrg then ok else authFail:
				s"Object exists and was submitted by $subm. Upload on behalf of $submittingOrg is therefore impossible."


	private def userAuthorizedBySubmitter(submConf: DataSubmitterConfig, uploader: UserId): Try[NotUsed] =
		val userId = uploader.email
		if !submConf.authorizedUserIds.contains(userId) then authFail:
			s"User '$userId' is not authorized to upload on behalf of submitter '${submConf.submittingOrganization}'"
		else ok


	private def userAuthorizedByThemesAndProjects(spec: DataObjectSpec, submConf: DataSubmitterConfig): Try[NotUsed] = {
		if(!submConf.authorizedThemes.fold(true)(_.contains(spec.theme.self.uri)))
			authFail(s"Submitter is not authorized to upload data linked to the '${spec.theme.self.uri}' theme")
		else if(!submConf.authorizedProjects.fold(true)(_.contains(spec.project.self.uri)))
			authFail(s"Submitter is not authorized to upload data linked to the '${spec.project.self.uri}' project")
		else ok
	}

	private def submitterAuthorizedByCollectionCreator(submConf: DataSubmitterConfig, coll: Sha256Sum)(using Envri, CollConn): Try[NotUsed] =
		val collIri = vocab.getCollection(coll)
		metaReader.getCreatorIfCollExists(collIri).result.get.fold(ok): creator =>
			if(creator === submConf.submittingOrganization)
				ok
			else
				authFail(s"Collection already exists and was submitted by $creator, " +
					s"whereas you are submitting on behalf of ${submConf.submittingOrganization}")


	private def collMemberListOk(coll: StaticCollectionDto, hash: Sha256Sum)(using Envri, GlobConn): Try[NotUsed] =
		val collUri = vocab.getCollection(hash)
		val metaReader = servers.metaReader
		import metaReader.{collectionExists, dataObjExists, docObjExists}
		if !collectionExists(collUri) && coll.members.isEmpty
		then userFail("Creating empty static collections is not allowed")
		else
			coll.members.find{item =>
				!collectionExists(item.toRdf) && !dataObjExists(item.toRdf) && !docObjExists(item.toRdf)
			} match
				case None => ok
				case Some(item) => userFail(s"Neither collection nor object was found at $item")


	private def userAuthorizedByProducer(meta: DataObjectDto, submConf: DataSubmitterConfig)(using MetaConn): Validated[NotUsed] =
		val producer = meta.specificInfo.fold(
			l3 => l3.production.hostOrganization.getOrElse(l3.production.creator),
			_.station
		)

		val errors = Buffer.empty[String]

		for prodOrgClass <- submConf.producingOrganizationClass do
			if !resourceHasType(producer.toRdf, prodOrgClass.toRdf) then
				errors += s"Data producer '$producer' does not belong to class '$prodOrgClass'"

		for prodOrg <- submConf.producingOrganization do
			if producer != prodOrg then
				errors += s"User is not authorized to upload on behalf of producer '$producer'"

		if errors.isEmpty then Validated.ok(NotUsed) else new Validated(None, errors.toSeq)

	private def validateActors(meta: DataObjectDto)(using MetaConn): Try[NotUsed] =

		def isOrg(iri: IRI) = hasStatement(iri, metaVocab.hasName, null)
		def isPerson(iri: IRI) = resourceHasType(iri, metaVocab.personClass)
		def isActor(iri: IRI) = isOrg(iri) || isPerson(iri)

		def validate(prod: DataProductionDto): Try[NotUsed] =
			val errors = Buffer.empty[String]
			if !isActor(prod.creator.toRdf) then errors += s"Invalid creator URL ${prod.creator}"

			for(contributor <- prod.contributors if !isActor(contributor.toRdf))
				errors += s"Invalid contributor URL $contributor"

			for (org <- prod.hostOrganization if !isOrg(org.toRdf))
				errors += s"Invalid host organization URL $org"

			if errors.isEmpty then ok else userFail(errors.mkString("\n"))

		meta.specificInfo match
			case Left(spTempMeta) => validate(spTempMeta.production)
			case Right(stationMeta) => stationMeta.production.fold(ok)(validate)


	private def validateTemporalCoverage(meta: DataObjectDto, spec: DataObjectSpec): Try[NotUsed] =

		def validate(interval: TimeInterval): Try[NotUsed] =
			val now = Instant.now()
			if now.compareTo(interval.start) < 0 || now.compareTo(interval.stop) < 0 then
				userFail("Temporal coverage cannot extend into the future")
			else if interval.start.compareTo(interval.stop) > 0 then
				userFail("Start date must come before end date in temporal coverage")
			else ok

		if spec.dataLevel >= 3 then ok else meta.specificInfo match
			case Left(spTempMeta) => validate(spTempMeta.temporal.interval)
			case Right(stationMeta) => stationMeta.acquisitionInterval.fold(ok)(validate)


	private def validateSpatialCoverage(meta: DataObjectDto)(using conn: DobjConn): Try[NotUsed] =
		def coverageExistsIn(covUri: URI)(using TriplestoreConnection): Boolean =
			val cov = covUri.toRdf
			hasStatement(cov, metaVocab.asGeoJSON, null) ||
			resourceHasType(cov, metaVocab.latLonBoxClass) ||
			resourceHasType(cov, metaVocab.positionClass)
		def customCoverageExists(covUri: URI) = coverageExistsIn(covUri)(using conn.primaryContextView)
		def stockCoverageExists(covUri: URI) = coverageExistsIn(covUri) && !customCoverageExists(covUri)

		meta.specificInfo match
			case Left(spTemp) => spTemp.spatial match
				case Left(geoFeature) => geoFeature.uri match
					case None => ok
					case Some(covUri) =>
						if customCoverageExists(covUri) then ok
						else userFail(
							"Spatial coverage was supplied with URI for metadata upload, but no prior spatial coverage " +
							"instance appears to exist with this URL in the target RDF graph of this data object. " +
							"If you intend to upload a custom spatial coverage for this object, do not use a URL. " +
							"If you want to reuse a 'stock' spatial coverage, supply a URI only instead of GeoCoverage object."
						)
				case Right(covUri) =>
					if stockCoverageExists(covUri) then ok
					else if customCoverageExists(covUri) then userFail(
						s"There exists a custom spatial coverage with URI $covUri. Please use full GeoFeature object without URI " +
						"inside the metadata upload package, rather than just the URI reference."
					)
					else userFail(s"No 'stock' spatial coverage with URI $covUri")
			case Right(_) => ok


	private def validateDescription(descr: Option[String]): Try[NotUsed] = descr.fold(ok)(
		doc => if (doc.length <= 5000) then ok else userFail("Description is too long, maximum 5000 characters")
	)

	private val formatsWithRowInfoInHeader = Set(
		metaVocab.wdcggFormat, metaVocab.atcProductFormat, metaVocab.netCDFTimeSeriesFormat, metaVocab.asciiAtcFlaskTimeSer
	)

	private def validateFormatsByFileExt(dto: DataObjectDto, spec: DataObjectSpec): Try[NotUsed] =
		val fileExtension = dto.fileName.split("\\.").last

		val formatUri = spec.format.self.uri
		inline def isNetCdf = formatUri === metaVocab.netCDFTimeSeriesFormat || formatUri === metaVocab.netCDFSpatialFormat
		inline def isExcel = formatUri === metaVocab.microsoftExcelFormat

		if isNetCdf && fileExtension != "nc" then
			userFail("Expected NetCDF file")
		else if isExcel && fileExtension != "xlsx" then
			userFail("Expected Microsoft Excel file")
		else ok


	private def validateForFormat(meta: DataObjectDto, spec: DataObjectSpec, subm: DataSubmitterConfig)(using Envri, MetaConn): Validated[NotUsed] =

		val errors = scala.collection.mutable.Buffer.empty[String]

		meta.specificInfo match
			case Left(spTempMeta) =>
				if spec.specificDatasetType != DatasetType.SpatioTemporal
				then errors += "Wrong type of dataset for this object spec (must be spatiotemporal)"
				else
					for vars <- spTempMeta.variables do spec.datasetSpec.fold(
						errors += s"Data object specification ${spec.self.uri} lacks a dataset specification; cannot accept variable info."
					): dsSpec =>
						val valTypeLookupV = metaReader.getValTypeLookup(dsSpec.self.uri.toRdf)
						errors ++= valTypeLookupV.errors
						for valTypeLookup <- valTypeLookupV; varName <- vars do
							if valTypeLookup.lookup(varName).isEmpty then errors +=
								s"Variable name '$varName' is not compatible with dataset specification ${dsSpec.self.uri}"

			case Right(stationMeta) =>
				if spec.specificDatasetType != DatasetType.StationTimeSeries
				then errors += "Wrong type of dataset for this object spec (must be station-specific time series)"
				else
					if(spec.datasetSpec.isEmpty && stationMeta.acquisitionInterval.isEmpty)
						errors += "Must provide 'acquisitionInterval' with start and stop timestamps."

					if(
						(spec.datasetSpec.isDefined) && stationMeta.nRows.isEmpty &&
						!formatsWithRowInfoInHeader.contains(spec.format.self.uri.toRdf)
					) errors += "Must provide 'nRows' with number of rows in the uploaded data file."

					if(subm.submittingOrganization === vocab.atc){
						val instruments = stationMeta.instruments
						errors ++= instruments.flatMap(atcInstrumentError)
						if(instruments.isEmpty)
							errors += "Instrument URL(s) expected for ATC time series"
						if(stationMeta.samplingHeight.isEmpty && spec.dataLevel > 0) errors += "Must provide sampling height"
					}

					if (summon[Envri] == Envri.SITES && stationMeta.site.isEmpty)
						errors += "Must provide 'location/ecosystem'"

		new Validated(Some(NotUsed).filter(_ => errors.isEmpty), errors.toSeq)
	end validateForFormat

	private def atcInstrumentError(uri: URI)(using Envri): Option[String] =
		val base = vocab.getInstrument(UriId("ATC_"))
		if uri.toString.startsWith(base.toString) then None
		else Some(s"ATC instrument URL is expected to start with $base")


	private def validatePreviousCollectionVersion(prevVers: OptionalOneOrSeq[Sha256Sum], newCollHash: Sha256Sum)(using Envri, CollConn): Try[NotUsed] =
		prevVers.flattenToSeq.iterator.map{coll =>
			val prevColl = vocab.getCollection(coll)
			if metaReader.collectionExists(prevColl) then
				bothOk(
					if coll != newCollHash then ok
					else userFail(s"A collection cannot be a next version of itself")
				,
					for
						inServer <- servers.collectionServer.toTry(new MetadataException(_))
						exceptColl = vocab.getCollection(newCollHash)
						_ <- inServer.access:
							scoped.hasNoOtherDeprecators(prevColl, exceptColl, false, false)
					yield NotUsed
				)
			else
				userFail(s"Previous-version collection was not found: $coll")
		}
		.foldLeft(ok)(bothOk(_, _))

end UploadValidator
