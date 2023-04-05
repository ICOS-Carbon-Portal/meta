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
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.DataObjectSpec
import se.lu.nateko.cp.meta.core.data.DatasetType
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer.Hash
import se.lu.nateko.cp.meta.services.upload.CpmetaFetcher
import se.lu.nateko.cp.meta.services.upload.DataObjectInstanceServers
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

private class ScopedValidator(val server: InstanceServer, vocab: CpVocab) extends CpmetaFetcher:

	type Validated[Dto <: ObjectUploadDto] <: Try[ObjectUploadDto] = Dto match
		case DataObjectDto => Try[DataObjectDto]
		case DocObjectDto => Try[DocObjectDto]

	given vf: ValueFactory = server.factory

	def existsAndIsCompleted(item: IRI): Try[NotUsed] =
		if isComplete(item) then ok
		else userFail(s"Item $item was not found or has not been successfully uploaded")

	def validatePrevVers(dto: ObjectUploadDto)(using Envri): Try[NotUsed] =
		val prevVersions = dto.isNextVersionOf.flattenToSeq

		if dto.nextVersionIsPartial && prevVersions.length > 1
			then userFail("Cannot deprecate multiple objects with partial upload")
		else
			prevVersions.iterator.map{prevHash =>
				if prevHash == dto.hashSum then
					userFail("Data/doc object cannot be a next version of itself")

				else
					val prevDobj = vocab.getStaticObject(prevHash)
					bothOk(
						existsAndIsCompleted(prevDobj),
						{
							val except = vocab.getStaticObject(dto.hashSum)
							hasNoOtherDeprecators(prevDobj, except, true, dto.nextVersionIsPartial)
						}
					)
			}
			.foldLeft(ok)(bothOk(_, _))

	def validateLicence(dto: ObjectUploadDto): Try[NotUsed] =
		dto.references.flatMap(_.licence).fold(Success(NotUsed)){licUri =>
			if(server.getTypes(licUri.toRdf).contains(metaVocab.dcterms.licenseDocClass)) ok
			else userFail(s"Unknown licence $licUri")
		}

	def hasNoOtherDeprecators(item: IRI, except: IRI, amongCompleted: Boolean, partialUpload: Boolean): Try[NotUsed] =

		val allowedPlainColl: Option[IRI] =
			if partialUpload then server
				.getStatements(None, Some(metaVocab.dcterms.hasPart), Some(except))
				.collect{
					case Rdf4jStatement(coll, _, _) if isPlainCollection(coll) &&
						server.hasStatement(coll, metaVocab.isNextVersionOf, item) => coll
				}
				.toIndexedSeq
				.headOption
			else None

		val deprs = server
			.getStatements(None, Some(metaVocab.isNextVersionOf), Some(item))
			.collect{
				case Rdf4jStatement(depr, _, _) if {
						if partialUpload then allowedPlainColl match
							case Some(coll) => coll != depr
							case None => !isPlainCollection(depr)
						else
							depr != except
					} => depr
			}
			.toIndexedSeq

		val otherDeprs = if amongCompleted then deprs.filter(isComplete) else deprs

		otherDeprs.headOption.fold(ok){depr =>
			val msg = new StringBuilder
			msg ++= s"Item $item already has a newer version"
			val deprIsPlainColl = isPlainCollection(depr)
			if deprIsPlainColl && !partialUpload then
				msg ++= ". It looks like the newer version is a group of objects, not a single one."
				msg ++= " You may want to use the 'Partial upload' flag if the object you are trying to upload is"
				msg ++= " supposed to be one of the group."
			else
				if !deprIsPlainColl then msg ++= s" $depr"
				msg ++= ". You may want to upload your object as a new version of "
				getLatestVersion(item) match
						case Left(single) =>
							msg ++= single.toString
						case Right(few) =>
							msg ++= "one (or more) of the following: "
							msg ++= few.mkString(", ")
			userFail(msg.mkString)
		}
	end hasNoOtherDeprecators

	def validateFileName[Dto <: ObjectUploadDto](dto: Dto)(using Envri): Validated[Dto] =
		if dto.duplicateFilenameAllowed && !dto.autodeprecateSameFilenameObjects then passValidation(dto)
		else
			val allDuplicates = server
				.getStatements(None, Some(metaVocab.hasName), Some(vf.createLiteral(dto.fileName)))
				.collect{case Rdf4jStatement(subj, _, _) if !isDeprecated(subj) => subj.toJava}
				.collect{case Hash.Object(hash) if hash != dto.hashSum => hash} //can re-upload metadata for existing object
				.toIndexedSeq

			if allDuplicates.isEmpty then passValidation(dto)
			else
				val deprecated = dto.isNextVersionOf.flattenToSeq
				if allDuplicates.exists(deprecated.contains) then passValidation(dto)

				else if dto.autodeprecateSameFilenameObjects then
					withDeprecations(dto, deprecated ++ allDuplicates)

				else failValidation(
					userFail(
						s"File name is already taken by other object(s) (${allDuplicates.mkString(", ")})." +
						" Please deprecate older version(s) or set either 'duplicateFilenameAllowed' or " +
						"'autodeprecateSameFilenameObjects' flag in 'references' to 'true'"
					)
				)
	end validateFileName

	private def isDeprecated(item: IRI): Boolean = server
		.getStatements(None, Some(metaVocab.isNextVersionOf), Some(item))
		.collect{case Rdf4jStatement(subj, _, _) if isComplete(subj) => true}
		.toIndexedSeq
		.nonEmpty

	def growingIsGrowing(
		dto: ObjectUploadDto,
		spec: DataObjectSpec,
		subm: DataSubmitterConfig
	)(using Envri): Try[NotUsed] = if(subm.submittingOrganization === vocab.atc) dto match {
		case DataObjectDto(
			_, _, _, _,
			Right(StationTimeSeriesDto(stationUri, _, _, _, _, Some(TimeInterval(_, acqStop)), _, _)),
			Some(Left(prevHash)), _, _
		) =>
			if(spec.dataLevel == 1 && spec.format.uri === metaVocab.atcProductFormat && spec.project.self.uri === vocab.icosProject){
				val prevDobj = vocab.getStaticObject(prevHash)
				val prevAcqStop = server.getUriValues(prevDobj, metaVocab.wasAcquiredBy).flatMap{acq =>
					getOptionalInstant(acq, metaVocab.prov.endedAtTime)
				}
				if(prevAcqStop.exists(_ == acqStop)) userFail(
					"The supposedly NRT growing data object you intend to upload " +
					"has not grown in comparison with its older version.\n" +
					s"Older object: $prevDobj , station: $stationUri, new (claimed) acquisition stop time: $acqStop"
				) else ok
			} else ok
		case _ => ok
	} else ok

	def validateMoratorium(meta: DataObjectDto)(using Envri): Try[NotUsed] =
		meta.references.flatMap(_.moratorium).fold(ok) {moratorium =>
			val iri = vocab.getStaticObject(meta.hashSum)
			val uploadComplete = server.hasStatement(Some(iri), Some(metaVocab.hasSizeInBytes), None)

			def validateMoratorium =
				if moratorium.compareTo(Instant.now()) > 0 then ok
				else userFail("Moratorium date must be in the future")

			if !uploadComplete then validateMoratorium else
				val submissionIri = vocab.getSubmission(meta.hashSum)
				val submissionEndDate = getSingleInstant(submissionIri, metaVocab.prov.endedAtTime)

				val uploadedDobjUnderMoratorium = submissionEndDate.compareTo(Instant.now()) > 0

				if uploadedDobjUnderMoratorium then validateMoratorium
				else userFail("Moratorium only allowed if object has not already been published")
		}

	private def withDeprecations[Dto <: ObjectUploadDto](dto: Dto, deprecated: Seq[Sha256Sum]): Validated[Dto] =
		val nextVers: OptionalOneOrSeq[Sha256Sum] = Some(Right(deprecated))
		dto match
			case dobj: DataObjectDto => Success(dobj.copy(isNextVersionOf = nextVers))
			case doc: DocObjectDto => Success(doc.copy(isNextVersionOf = nextVers))

	private def passValidation[Dto <: ObjectUploadDto](dto: Dto): Validated[Dto] = dto match
		case dobj: DataObjectDto => Success(dobj)
		case doc: DocObjectDto => Success(doc)

	private def failValidation[Dto <: ObjectUploadDto](err: Failure[Nothing]) = err.asInstanceOf[Validated[Dto]]
end ScopedValidator