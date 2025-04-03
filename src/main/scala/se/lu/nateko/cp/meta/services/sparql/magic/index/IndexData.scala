package se.lu.nateko.cp.meta.services.sparql.magic.index

import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.model.{IRI, Literal, Statement, Value}
import org.roaringbitmap.buffer.MutableRoaringBitmap
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.core.algo.DatetimeHierarchicalBitmap.DateTimeGeo
import se.lu.nateko.cp.meta.core.algo.{DatetimeHierarchicalBitmap, HierarchicalBitmap}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer.Hash
import se.lu.nateko.cp.meta.services.sparql.index.*
import se.lu.nateko.cp.meta.services.sparql.index.StringHierarchicalBitmap.StringGeo
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.{===, Rdf4jStatement, asString, toJava}
import se.lu.nateko.cp.meta.utils.{asOptInstanceOf, parseCommaSepList, parseJsonStringArray}

import java.time.Instant
import scala.collection.IndexedSeq as IndSeq
import scala.collection.mutable.{AnyRefMap, ArrayBuffer}
import org.roaringbitmap.buffer.BufferFastAggregation
import org.roaringbitmap.buffer.ImmutableRoaringBitmap
import org.eclipse.rdf4j.model.Resource

final class DataStartGeo(objs: IndSeq[ObjEntry]) extends DateTimeGeo(objs(_).dataStart)
final class DataEndGeo(objs: IndSeq[ObjEntry]) extends DateTimeGeo(objs(_).dataEnd)
final class SubmStartGeo(objs: IndSeq[ObjEntry]) extends DateTimeGeo(objs(_).submissionStart)
final class SubmEndGeo(objs: IndSeq[ObjEntry]) extends DateTimeGeo(objs(_).submissionEnd)
final class FileNameGeo(objs: IndSeq[ObjEntry]) extends StringGeo(objs.apply(_).fName)

final case class StatKey(spec: IRI, submitter: IRI, station: Option[IRI], site: Option[IRI])
final case class StatEntry(key: StatKey, count: Int)

def emptyBitmap = MutableRoaringBitmap.bitmapOf()

final class IndexData(nObjects: Int)(
	val objs: ArrayBuffer[ObjEntry] = new ArrayBuffer(nObjects),
	val idLookup: AnyRefMap[Sha256Sum, Int] = new AnyRefMap[Sha256Sum, Int](nObjects * 2),
	val specs: ArrayBuffer[IRI] = new ArrayBuffer(nObjects),
	val keywordToSpecs: AnyRefMap[String, MutableRoaringBitmap] = new AnyRefMap[String, MutableRoaringBitmap](nObjects),
	val boolMap: AnyRefMap[BoolProperty, MutableRoaringBitmap] = AnyRefMap.empty,
	val categMaps: AnyRefMap[CategProp, AnyRefMap[?, MutableRoaringBitmap]] = AnyRefMap.empty,
	val contMap: AnyRefMap[ContProp, HierarchicalBitmap[?]] = AnyRefMap.empty,
	val stats: AnyRefMap[StatKey, MutableRoaringBitmap] = AnyRefMap.empty,
	val initOk: MutableRoaringBitmap = emptyBitmap
) extends Serializable:
	private val log = LoggerFactory.getLogger(getClass())

	private def dataStartBm = DatetimeHierarchicalBitmap(DataStartGeo(objs))
	private def dataEndBm = DatetimeHierarchicalBitmap(DataEndGeo(objs))
	private def submStartBm = DatetimeHierarchicalBitmap(SubmStartGeo(objs))
	private def submEndBm = DatetimeHierarchicalBitmap(SubmEndGeo(objs))
	private def fileNameBm = StringHierarchicalBitmap(FileNameGeo(objs))
	def copySpecs(): Array[IRI] = {
		specs.toArray()
	}


	def boolBitmap(prop: BoolProperty): MutableRoaringBitmap = boolMap.getOrElseUpdate(prop, emptyBitmap)

	def bitmap(prop: ContProp): HierarchicalBitmap[prop.ValueType] =
		contMap.getOrElseUpdate(
			prop,
			prop match {
				/** Important to maintain type consistency between props and HierarchicalBitmaps here*/
				case FileName => fileNameBm
				case FileSize => FileSizeHierarchicalBitmap(objs)
				case SamplingHeight => SamplingHeightHierarchicalBitmap(objs)
				case DataStart => dataStartBm
				case DataEnd => dataEndBm
				case SubmissionStart => submStartBm
				case SubmissionEnd => submEndBm
			}
		).asInstanceOf[HierarchicalBitmap[prop.ValueType]]

	def categMap(prop: CategProp): AnyRefMap[prop.ValueType, MutableRoaringBitmap] = categMaps
				keywordCategoryMap().asInstanceOf[AnyRefMap[prop.ValueType, ImmutableRoaringBitmap]]
	def getKeywordsBitmap(keywords: Seq[String]): ImmutableRoaringBitmap = {
		val kwMap = getCategoryMap(Keyword)
		val kwObjects: Seq[ImmutableRoaringBitmap] = keywords.map(keyword =>
			kwMap(keyword)
		)

		val kwSpecs: Seq[IRI] = getKeywordSpecs(keywords)

		val specMap: AnyRefMap[IRI, MutableRoaringBitmap] = getCategoryMap(Spec)
		val specObjects: Seq[ImmutableRoaringBitmap] = kwSpecs.map(spec =>
			specMap(spec)
		)

		BufferFastAggregation.or((specObjects ++ kwObjects)*)
	}

		.getOrElseUpdate(prop, new AnyRefMap[prop.ValueType, MutableRoaringBitmap])
		.asInstanceOf[AnyRefMap[prop.ValueType, MutableRoaringBitmap]]
	}

	private def keywordCategoryMap(): AnyRefMap[String, MutableRoaringBitmap] = {
		val kwIndex: AnyRefMap[String, MutableRoaringBitmap] =
			categMaps
				.getOrElse(Keyword, new AnyRefMap[String, MutableRoaringBitmap])
				.asInstanceOf[AnyRefMap[String, MutableRoaringBitmap]]

		val objKeywords = (categMaps
			.getOrElse(Keyword, new AnyRefMap[String, MutableRoaringBitmap])
			.asInstanceOf[AnyRefMap[String, MutableRoaringBitmap]]).keySet

		val keywords = keywordToSpecs.keySet ++ objKeywords

		val res = new AnyRefMap[String, MutableRoaringBitmap]
		val specMap: AnyRefMap[IRI, MutableRoaringBitmap] = getCategoryMap(Spec)

		keywords.foreach((kw: String) =>
			val kwObjects = kwIndex.getOrElse(kw, emptyBitmap)
			val specObjects: Seq[ImmutableRoaringBitmap] = getKeywordSpecs(Seq(kw)).flatMap(specMap.get)

			val combined = BufferFastAggregation.or((specObjects :+ kwObjects)*)
			if (!combined.isEmpty) {
				res.put(kw, combined)
			}
		)

		println(s"res: $res")

		res
	}

	private def getKeywordSpecs(keywords: Seq[String]): Seq[IRI] = {
		val bitmap = BufferFastAggregation.or(keywords.flatMap(keywordToSpecs.get)*)
		val result: ArrayBuffer[IRI] = new ArrayBuffer();

		bitmap.forEach(index => {
			result += specs(index)
		})

		result.toSeq
	}

	def processUpdate(statement: Rdf4jStatement, isAssertion: Boolean, vocab: CpmetaVocab)(using StatementSource): Unit = {
		import vocab.*
		import vocab.prov.{wasAssociatedWith, startedAtTime, endedAtTime}
		import vocab.dcterms.hasPart
		import statement.{subj, pred, obj}

		pred match {
			case `hasObjectSpec` =>
				obj match {
					case spec: IRI =>
						{
							getDataObject(subj).foreach { oe =>
								updateCategSet(getCategoryMap(Spec), spec, oe.idx, isAssertion)
								if (isAssertion) {
									if (oe.spec != null) removeStat(oe, initOk)
									oe.spec = spec
									addStat(oe, initOk)
								} else if (spec === oe.spec) {
									removeStat(oe, initOk)
									oe.spec = null
								}
							}
						}

						// Add spec if it is not already indexed
						if (isAssertion) {
							val found = specs.find(_ == spec).isDefined
							if (!found) {
								specs += spec
								given CpmetaVocab = vocab
								val keywords = getAssociatedKeywords(spec)
								updateSpecKeywords(spec, true, keywords)
							}
						}
				}

			case `hasName` =>
				getDataObject(subj).foreach { oe =>
					val fName = obj.stringValue
					if (isAssertion) oe.fName = fName
					else if (oe.fName == fName) { oe.fName = null }
					handleContinuousPropUpdate(FileName, fName, oe.idx, isAssertion)
				}

			case `wasAssociatedWith` =>
				subj match {
					case CpVocab.Submission(hash) =>
						val oe = getObjEntry(hash)
						removeStat(oe, initOk)
						oe.submitter = targetUri(obj, isAssertion)
						if (isAssertion) addStat(oe, initOk)
						obj match
							case subm: IRI => updateCategSet(getCategoryMap(Submitter), subm, oe.idx, isAssertion)

					case CpVocab.Acquisition(hash) =>
						val oe = getObjEntry(hash)
						removeStat(oe, initOk)
						oe.station = targetUri(obj, isAssertion)
						if (isAssertion) addStat(oe, initOk)
						obj match
							case stat: IRI => updateCategSet(getCategoryMap(Station), Some(stat), oe.idx, isAssertion)

					case _ =>
				}

			case `wasPerformedAt` => subj match {
					case CpVocab.Acquisition(hash) =>
						val oe = getObjEntry(hash)
						removeStat(oe, initOk)
						oe.site = targetUri(obj, isAssertion)
						if (isAssertion) addStat(oe, initOk)
						obj match
							case site: IRI => updateCategSet(getCategoryMap(Site), Some(site), oe.idx, isAssertion)

					case _ =>
				}

			case `hasStartTime` =>
				ifDateTime(obj) { dt =>
					getDataObject(subj).foreach { oe =>
						oe.dataStart = dt
						handleContinuousPropUpdate(DataStart, dt, oe.idx, isAssertion)
					}
				}

			case `hasEndTime` =>
				ifDateTime(obj) { dt =>
					getDataObject(subj).foreach { oe =>
						oe.dataEnd = dt
						handleContinuousPropUpdate(DataEnd, dt, oe.idx, isAssertion)
					}
				}

			case `startedAtTime` =>
				ifDateTime(obj) { dt =>
					subj match {
						case CpVocab.Acquisition(hash) =>
							val oe = getObjEntry(hash)
							oe.dataStart = dt
							handleContinuousPropUpdate(DataStart, dt, oe.idx, isAssertion)
						case CpVocab.Submission(hash) =>
							val oe = getObjEntry(hash)
							oe.submissionStart = dt
							handleContinuousPropUpdate(SubmissionStart, dt, oe.idx, isAssertion)
						case _ =>
					}
				}

			case `endedAtTime` =>
				ifDateTime(obj) { dt =>
					subj match
						case CpVocab.Acquisition(hash) =>
							val oe = getObjEntry(hash)
							oe.dataEnd = dt
							handleContinuousPropUpdate(DataEnd, dt, oe.idx, isAssertion)
						case CpVocab.Submission(hash) =>
							val oe = getObjEntry(hash)
							oe.submissionEnd = dt
							handleContinuousPropUpdate(SubmissionEnd, dt, oe.idx, isAssertion)
						case _ =>
				}

			case `isNextVersionOf` =>
				getDataObject(obj).foreach { oe =>
					val deprecated = boolBitmap(DeprecationFlag)
					if isAssertion then
						if !deprecated.contains(oe.idx) then // to prevent needless work
							val subjIsDobj = getDataObject(subj).map { deprecator =>
								deprecator.isNextVersion = true
								// only fully-uploaded deprecators can actually deprecate:
								if deprecator.size > -1 then
									deprecated.add(oe.idx)
									log.debug(s"Marked object ${deprecator.hash.id} as a deprecator of ${oe.hash.id}")
								else
									log.debug(s"Object ${deprecator.hash.id} wants to deprecate ${oe.hash.id} but is not fully uploaded yet")
							}.isDefined
							if !subjIsDobj then
								subj.toJava match
									case Hash.Collection(_) =>
										// proper collections are always fully uploaded
										deprecated.add(oe.idx)
									case _ => subj match
											case CpVocab.NextVersColl(_) =>
												if nextVersCollIsComplete(subj, vocab)
												then deprecated.add(oe.idx)
											case _ =>
					else if
						deprecated.contains(oe.idx) && // this was to prevent needless repo access
						!StatementSource.hasStatement(null, isNextVersionOf, obj)
					then deprecated.remove(oe.idx)
				}

			case `hasSizeInBytes` =>
				ifLong(obj) { size =>
					getDataObject(subj).foreach { oe =>
						inline def isRetraction = oe.size == size && !isAssertion
						if isAssertion then oe.size = size
						else if isRetraction then oe.size = -1

						if oe.isNextVersion then
							log.debug(s"Object ${oe.hash.id} appears to be a deprecator and just got fully uploaded. Will update the 'old' objects.")
							val deprecated = boolBitmap(DeprecationFlag)

							val directPrevVers: IndexedSeq[Int] =
								StatementSource.getStatements(subj, isNextVersionOf, null)
									.flatMap(st => getDataObject(st.getObject).map(_.idx))
									.toIndexedSeq

							directPrevVers.foreach { oldIdx =>
								if isAssertion then deprecated.add(oldIdx)
								else if isRetraction then deprecated.remove(oldIdx)
								log.debug(s"Marked ${objs(oldIdx).hash.id} as ${if isRetraction then "non-" else ""}deprecated")
							}
							if directPrevVers.isEmpty then
								getIdxsOfPrevVersThroughColl(subj, vocab) match
									case None =>
										log.warn(s"Object ${oe.hash.id} is marked as a deprecator but has no associated old versions")
									case Some(throughColl) => if isAssertion then deprecated.add(throughColl)

						if (size >= 0) handleContinuousPropUpdate(FileSize, size, oe.idx, isAssertion)
					}
				}

			case `hasPart` => if isAssertion then
					subj match
						case CpVocab.NextVersColl(hashOfOld) => getDataObject(obj).foreach { oe =>
								oe.isNextVersion = true
								if oe.size > -1 then
									boolMap(DeprecationFlag).add(getObjEntry(hashOfOld).idx)
							}
						case _ =>

			case `hasSamplingHeight` => ifFloat(obj) { height =>
					subj match {
						case CpVocab.Acquisition(hash) =>
							val oe = getObjEntry(hash)
							if (isAssertion) oe.samplingHeight = height
							else if (oe.samplingHeight == height) oe.samplingHeight = Float.NaN
							handleContinuousPropUpdate(SamplingHeight, height, oe.idx, isAssertion)
						case _ =>
					}
				}

			case `hasActualColumnNames` => getDataObject(subj).foreach { oe =>
					updateStrArrayProp(obj, VariableName, parseJsonStringArray, oe.idx, isAssertion)
					updateHasVarList(oe.idx, isAssertion)
				}

			case `hasActualVariable` => obj match {
					case CpVocab.VarInfo(hash, varName) =>
						val oe = getObjEntry(hash)
						updateCategSet(getCategoryMap(VariableName), varName, oe.idx, isAssertion)
						updateHasVarList(oe.idx, isAssertion)
					case _ =>
				}

			case `hasAssociatedProject` => {
				val project = ensureIRI(obj)
				val spec = subj

				given CpmetaVocab = vocab
				val associatedKeywords = getAssociatedKeywords(spec)
				val projectKeywords = getKeywords(project)
				val specKeywords = getKeywords(spec)

				val newKeywords: Set[String] = specKeywords ++ modifyKeywords(isAssertion, associatedKeywords, projectKeywords)
				updateSpecKeywords(subj, true, newKeywords)
			}

			case `hasKeywords` =>
				given CpmetaVocab = vocab
				getDataObject(subj) match {
					case Some(oe) =>
						updateStrArrayProp(obj, Keyword, s => Some(parseCommaSepList(s)), oe.idx, isAssertion)
					case None => {
						val changedKeywords = parseCommaSepList(obj.stringValue()).toSet

						// TODO: If we can assume specs are always inserted before their hasKeywords triple,
						//			 we could use the `specs` array to know if this is a spec.
						if (StatementSource.hasStatement(null, vocab.hasObjectSpec, subj)) {
							val existingKeywords = getAssociatedKeywords(ensureIRI(subj))
							updateSpecKeywords(subj, isAssertion, modifyKeywords(isAssertion, existingKeywords, changedKeywords))
						} else {
							val project = subj
							val projectSpecs = StatementSource.getStatements(null, vocab.hasAssociatedProject, project).map(_.getSubject())
							for (spec <- projectSpecs) {
								given CpmetaVocab = vocab
								val associatedKeywords = getAssociatedKeywords(ensureIRI((spec)))
								val specKeywords = getKeywords(ensureIRI(spec))

								val newKeywords: Set[String] = specKeywords ++ modifyKeywords(isAssertion, associatedKeywords, changedKeywords)
								updateSpecKeywords(subj, true, newKeywords)
							}
				}

			case _ =>
		}
	}

	private def modifyKeywords(isAssertion: Boolean, existing: Set[String], changed: Set[String]): Set[String] = {
		if (isAssertion) {
			existing ++ changed
		} else {
			existing -- changed
		}
	}

	private def getSpecObjects(spec: Resource)(using vocab: CpmetaVocab)(using StatementSource): Iterator[Resource] = {
		// This is potentially expensive, and could be optimized by keeping additional
		// index information on which data objects are associated with each spec.
		// This could be done selectively during initial index building,
		// only for specs with a large number of associated objects.
		StatementSource.getStatements(null, vocab.hasObjectSpec, spec).map(_.getSubject())
	}

	private def getAssociatedKeywords(spec: IRI)(using vocab: CpmetaVocab)(using StatementSource): Set[String] = {
		if (spec == null) {
			return Set.empty;
		}

		val projects = StatementSource.getUriValues(spec, vocab.hasAssociatedProject)
		val projectKeywords = projects
			.flatMap(project => getKeywords(project))
			.toSet

		getKeywords(spec) ++ projectKeywords
	}

	private def ensureIRI(value: Value): IRI = {
		value match {
			case iri: IRI => iri
		}
	}

	private def getKeywords(subject: IRI)(using vocab: CpmetaVocab)(using StatementSource): Set[String] = {
		if (subject == null) {
			return Set.empty;
		}

		StatementSource.getValues(subject, vocab.hasKeywords)
			.flatMap(parseKeywords)
			.flatten()
			.toSet
	}

	private def updateSpecKeywords(spec: IRI, isAssertion: Boolean, changedKeywords: Set[String]) = {
		var id = specs.indexOf(spec);
		val found = id > 0
		if (!found) {
			specs += spec
			id = specs.length - 1
		}
		/*
		println(s"id: $id")
		println(s"changedKeywords: $changedKeywords")
		 */

		for (kw <- changedKeywords) {
			updateCategSet(keywordToSpecs, kw, id, isAssertion)
		}
		// println(s"keywordToSpecs: $keywordToSpecs")
	}

	private def parseKeywords(obj: Value): Option[Array[String]] = {
		obj.asOptInstanceOf[Literal].flatMap(asString).map(parseCommaSepList)
	}

	def updateStrArrayProp(
		obj: Value,
		prop: StringCategProp,
		parser: String => Option[Array[String]],
		idx: Int,
		isAssertion: Boolean
	): Unit = {
		obj.asOptInstanceOf[Literal].flatMap(asString).flatMap(parser).toSeq.flatten.foreach { strVal =>
			updateCategSet(getCategoryMap(prop), strVal, idx, isAssertion)
		}
	}

	def getObjEntry(hash: Sha256Sum): ObjEntry = {
		idLookup.get(hash).fold {
			val canonicalHash = hash.truncate
			val oe = new ObjEntry(canonicalHash, objs.length, "")
			objs += oe
			idLookup += canonicalHash -> oe.idx
			oe
		}(objs.apply)
	}

	private def handleContinuousPropUpdate(
		prop: ContProp,
		key: prop.ValueType,
		idx: Int,
		isAssertion: Boolean
	): Unit = {

		def helpTxt = s"value $key of property $prop on object ${objs(idx).hash.base64Url}"
		if (isAssertion) {
			if (!bitmap(prop).add(key, idx)) {
				log.warn(s"Value already existed: asserted $helpTxt")
			}
		} else if (!bitmap(prop).remove(key, idx)) {
			log.warn(s"Value was not present: tried to retract $helpTxt")
		}
	}

	private def updateHasVarList(idx: Int, isAssertion: Boolean): Unit = {
		val hasVarsBm = boolBitmap(HasVarList)
		if (isAssertion) hasVarsBm.add(idx) else hasVarsBm.remove(idx)
	}

	private def nextVersCollIsComplete(obj: IRI, vocab: CpmetaVocab)(using StatementSource): Boolean =
		StatementSource.getStatements(obj, vocab.dcterms.hasPart, null)
			.collect:
				case Rdf4jStatement(_, _, member: IRI) => getDataObject(member).map: oe =>
						oe.isNextVersion = true
						oe.size > -1
			.flatten
			.toIndexedSeq
			.exists(identity)

	private def getIdxsOfPrevVersThroughColl(deprecator: IRI, vocab: CpmetaVocab)(using StatementSource): Option[Int] =
		StatementSource.getStatements(null, vocab.dcterms.hasPart, deprecator)
			.collect { case Rdf4jStatement(CpVocab.NextVersColl(oldHash), _, _) => getObjEntry(oldHash).idx }
			.toIndexedSeq
			.headOption

	private def getDataObject(dobj: Value): Option[ObjEntry] = dobj match
		case CpVocab.DataObject(hash, prefix) =>
			val entry = getObjEntry(hash)
			if (entry.prefix == "") {
				entry.prefix = prefix.intern()
			}
			Some(entry)

		case _ => None

	private def addStat(obj: ObjEntry, initOk: MutableRoaringBitmap): Unit = for key <- keyForDobj(obj) do
		stats.getOrElseUpdate(key, emptyBitmap).add(obj.idx)
		initOk.add(obj.idx)

	private def removeStat(obj: ObjEntry, initOk: MutableRoaringBitmap): Unit = for key <- keyForDobj(obj) do
		stats.get(key).foreach: bm =>
			bm.remove(obj.idx)
			if bm.isEmpty then { val _ = stats.remove(key) } // to prevent "orphan" URIs from lingering
		initOk.remove(obj.idx)

end IndexData

private def targetUri(obj: Value, isAssertion: Boolean) =
	if isAssertion && obj.isInstanceOf[IRI]
	then obj.asInstanceOf[IRI]
	else null

private def updateCategSet[T <: AnyRef](
	set: AnyRefMap[T, MutableRoaringBitmap],
	categ: T,
	idx: Int,
	isAssertion: Boolean
): Unit = {
	val bm = set.getOrElseUpdate(categ, emptyBitmap)
	if isAssertion then bm.add(idx)
	else
		bm.remove(idx)
		if bm.isEmpty then {
			val _ = set.remove(categ)
		}
}

private def keyForDobj(obj: ObjEntry): Option[StatKey] =
	if obj.spec == null || obj.submitter == null then None
	else
		Some(
			StatKey(obj.spec, obj.submitter, Option(obj.station), Option(obj.site))
		)

private def ifDateTime(dt: Value)(mod: Long => Unit): Unit = dt match
	case lit: Literal if lit.getDatatype === XSD.DATETIME =>
		try mod(Instant.parse(lit.stringValue).toEpochMilli)
		catch case _: Throwable => () // ignoring wrong dateTimes
	case _ =>

private def ifLong(dt: Value)(mod: Long => Unit): Unit = dt match
	case lit: Literal if lit.getDatatype === XSD.LONG =>
		try mod(lit.longValue)
		catch case _: Throwable => () // ignoring wrong longs
	case _ =>

private def ifFloat(dt: Value)(mod: Float => Unit): Unit = dt match
	case lit: Literal if lit.getDatatype === XSD.FLOAT =>
		try mod(lit.floatValue)
		catch case _: Throwable => () // ignoring wrong floats
	case _ =>
