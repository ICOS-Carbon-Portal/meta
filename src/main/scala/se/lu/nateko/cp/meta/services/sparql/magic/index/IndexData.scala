package se.lu.nateko.cp.meta.services.sparql.magic.index

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.roaringbitmap.buffer.BufferFastAggregation
import org.roaringbitmap.buffer.ImmutableRoaringBitmap
import org.roaringbitmap.buffer.MutableRoaringBitmap
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.core.algo.DatetimeHierarchicalBitmap
import se.lu.nateko.cp.meta.core.algo.DatetimeHierarchicalBitmap.DateTimeGeo
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.EnvriResolver
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer.Hash
import se.lu.nateko.cp.meta.services.sparql.index.*
import se.lu.nateko.cp.meta.services.sparql.index.StringHierarchicalBitmap.StringGeo
import se.lu.nateko.cp.meta.services.sparql.magic.ObjInfo
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.parseJsonStringArray
import se.lu.nateko.cp.meta.utils.rdf4j.===
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
import se.lu.nateko.cp.meta.utils.rdf4j.asString
import se.lu.nateko.cp.meta.utils.rdf4j.toJava

import java.time.Instant
import scala.collection.IndexedSeq as IndSeq
import scala.collection.mutable.AnyRefMap
import scala.collection.mutable.ArrayBuffer

// Categories listed here are retained, even if the associated bitmap is empty.
// Currently used for keeping track of Specs that have been seen, even if no objects are
// associed with them, or the last object is removed.
private val retainedCategoryKeys: Seq[CategProp] = Seq(Spec)

final class DataStartGeo(objs: IndSeq[ObjEntry]) extends DateTimeGeo(objs(_).dataStart)
final class DataEndGeo(objs: IndSeq[ObjEntry]) extends DateTimeGeo(objs(_).dataEnd)
final class SubmStartGeo(objs: IndSeq[ObjEntry]) extends DateTimeGeo(objs(_).submissionStart)
final class SubmEndGeo(objs: IndSeq[ObjEntry]) extends DateTimeGeo(objs(_).submissionEnd)
final class FileNameGeo(objs: IndSeq[ObjEntry]) extends StringGeo(objs.apply(_).fName)

final case class StatKey(spec: IRI, submitter: IRI, station: Option[IRI], site: Option[IRI])
final case class StatEntry(key: StatKey, count: Int)

def emptyBitmap = MutableRoaringBitmap.bitmapOf()

final class IndexData(nObjects: Int)(
	// These members are public only because of serialization, and should not be accessed directly.

	val objs: ArrayBuffer[ObjEntry] = new ArrayBuffer(nObjects),
	val idLookup: AnyRefMap[Sha256Sum, Int] = new AnyRefMap(nObjects * 2),
	val keywordsToSpecs: AnyRefMap[String, Set[IRI]] = AnyRefMap.empty,
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

	def boolBitmap(prop: BoolProperty): ImmutableRoaringBitmap = {
		mutableBoolBitmap(prop)
	}

	private def mutableBoolBitmap(prop: BoolProperty): MutableRoaringBitmap = {
		boolMap.getOrElseUpdate(prop, emptyBitmap)
	}

	def getObjectKeywords(objectIds: ImmutableRoaringBitmap): Iterable[String] = {
		categoryKeys(Keyword).collect {
			case keyword if !BufferFastAggregation.and(objectIds, keywordBitmap(Seq(keyword))).isEmpty() =>
				keyword
		}
	}

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

	def categoryBitmap(prop: CategProp, values: Iterable[prop.ValueType]): ImmutableRoaringBitmap = {
		prop match {
			case Keyword =>
				keywordBitmap(values.asInstanceOf[Iterable[Keyword.ValueType]])
			case _ => {
				val category = categMap(prop)
				BufferFastAggregation.or(values.map(v => category.getOrElse(v, emptyBitmap)).toSeq*)
			}
		}
	}

	def categoryBitmapBy(prop: CategProp, predicate: prop.ValueType => Boolean): ImmutableRoaringBitmap = {
		categoryBitmap(prop, categoryKeys(prop).filter(predicate))
	}

	def categoryKeys(prop: CategProp): Set[prop.ValueType] = {
		prop match
			case Keyword =>
				val keywordsToObjs = categMap(Keyword)
				Set.concat(keywordsToSpecs.keySet, keywordsToObjs.keySet)
					.map(_.asInstanceOf[prop.ValueType])
			case _ =>
				categMap(prop).keysIterator.toSet
	}

	private def categMap(prop: CategProp): AnyRefMap[prop.ValueType, MutableRoaringBitmap] = {
		categMaps
			.getOrElseUpdate(prop, new AnyRefMap[prop.ValueType, MutableRoaringBitmap])
			.asInstanceOf[AnyRefMap[prop.ValueType, MutableRoaringBitmap]]
	}

	private def keywordBitmap(keywords: Iterable[String]): ImmutableRoaringBitmap = {
		val specMap: AnyRefMap[IRI, MutableRoaringBitmap] = categMap(Spec)
		val specObjects = keywords.flatMap(keywordsToSpecs.get).flatten.flatMap(specMap.get)

		val objectMap = categMap(Keyword)
		val objects = keywords.flatMap(objectMap.get)

		BufferFastAggregation.or(LazyList(specObjects, objects).flatten*)
	}

	def processUpdate(
		statement: Rdf4jStatement, isAssertion: Boolean, vocab: CpmetaVocab
	)(using StatementSource, EnvriConfigs): Unit =
		import vocab.*
		import vocab.prov.{wasAssociatedWith, startedAtTime, endedAtTime}
		import vocab.dcterms.hasPart
		import statement.{subj, pred, obj}

		given CpmetaVocab = vocab
		val filterByEnvri: Boolean = summon[EnvriConfigs].size > 1

		pred match {
			case `hasObjectSpec` =>
				obj match {
					case spec: IRI => {
						getDataObject(subj).foreach { oe =>
							updateCategSet(Spec, spec, oe.idx, isAssertion)
							if (filterByEnvri) EnvriResolver.infer(subj.toJava).foreach: envri =>
								updateCategSet(EnvriProp, envri, oe.idx, isAssertion)
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
				}

			case RDF.TYPE => {
				if (obj == vocab.dataObjectSpecClass || obj == vocab.simpleObjectSpecClass) {
					registerSpec(subj)
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
							case subm: IRI => updateCategSet(Submitter, subm, oe.idx, isAssertion)

					case CpVocab.Acquisition(hash) =>
						val oe = getObjEntry(hash)
						removeStat(oe, initOk)
						oe.station = targetUri(obj, isAssertion)
						if (isAssertion) addStat(oe, initOk)
						obj match
							case stat: IRI => updateCategSet(Station, Some(stat), oe.idx, isAssertion)

					case _ =>
				}

			case `wasPerformedAt` => subj match {
					case CpVocab.Acquisition(hash) =>
						val oe = getObjEntry(hash)
						removeStat(oe, initOk)
						oe.site = targetUri(obj, isAssertion)
						if (isAssertion) addStat(oe, initOk)
						obj match
							case site: IRI => updateCategSet(Site, Some(site), oe.idx, isAssertion)

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
					val deprecated = mutableBoolBitmap(DeprecationFlag)
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
							val deprecated = mutableBoolBitmap(DeprecationFlag)

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
					asStringLiteral(obj).foreach { colNamesJsArr =>
						parseJsonStringArray(colNamesJsArr).toSeq.flatten.foreach(strVal =>
							updateCategSet(VariableName, strVal, oe.idx, isAssertion)
						)
					}
					updateHasVarList(oe.idx, isAssertion)
				}

			case `hasActualVariable` => obj match {
					case CpVocab.VarInfo(hash, varName) =>
						val oe = getObjEntry(hash)
						updateCategSet(VariableName, varName, oe.idx, isAssertion)
						updateHasVarList(oe.idx, isAssertion)
					case _ =>
				}

			case `hasAssociatedProject` => obj match
					case proj: IRI =>
						updateSpecProjectKeywords(subj, isAssertion, getKeywords(proj))
						registerSpec(subj)

					case _ =>

			case `hasKeywords` => parseKeywords(obj).foreach: changedKeywords =>
					getDataObject(subj) match {
						case Some(oe) => {
							changedKeywords.foreach { strVal =>
								updateCategSet(Keyword, strVal, oe.idx, isAssertion)
							}
						}
						case None =>
							if (changedKeywords.nonEmpty) {
								if (isSpec(subj)) {
									updateSpecOwnKeywords(subj, isAssertion, changedKeywords)
								} else { // assuming subj is a Project
									StatementSource.getPropValueHolders(vocab.hasAssociatedProject, subj)
										.foreach: spec =>
											updateSpecProjectKeywords(spec, isAssertion, changedKeywords)
								}
							}
					}

			case _ =>
		} // end pred match ...
	end processUpdate

	private def updateSpecOwnKeywords(
		spec: IRI,
		isAssertion: Boolean,
		changedSpecKeywords: Set[String]
	)(using CpmetaVocab, StatementSource): Unit = {
		val existingKeywords = getKeywords(spec)
		val projKeywords = getSpecProjectKeywords(spec)
		val newKeywords = projKeywords ++ modifySet(isAssertion, existingKeywords, changedSpecKeywords)

		setSpecKeywords(spec, newKeywords)
	}

	private def updateSpecProjectKeywords(
		spec: IRI,
		isAssertion: Boolean,
		changedProjectKeywords: Set[String]
	)(using CpmetaVocab, StatementSource): Unit = {
		val projKeywords = getSpecProjectKeywords(spec)
		val newKeywords = getKeywords(spec) ++ modifySet(isAssertion, projKeywords, changedProjectKeywords)

		setSpecKeywords(spec, newKeywords)
	}

	private def modifySet[T](add: Boolean, existing: Set[T], changed: Set[T]): Set[T] = {
		if (add) {
			existing ++ changed
		} else {
			existing -- changed
		}
	}

	private def updateCategSet[T <: CategProp](prop: T, categ: prop.ValueType, idx: Int, isAssertion: Boolean): Unit = {
		val mappings = categMap(prop)
		val bitmap = mappings.getOrElseUpdate(categ, emptyBitmap)
		if (isAssertion) {
			bitmap.add(idx)
		} else
			bitmap.remove(idx)
			if (bitmap.isEmpty && !retainedCategoryKeys.contains(prop)) {
				val _ = mappings.remove(categ)
			}
	}

	private def getSpecProjectKeywords(spec: IRI)(using CpmetaVocab, StatementSource): Set[String] = {
		StatementSource
			.getUriValues(spec, summon[CpmetaVocab].hasAssociatedProject)
			.flatMap(project => getKeywords(project))
			.toSet
	}

	private def setSpecKeywords(spec: IRI, newKeywords: Set[String]) = {
		// Existing keywords plus new ones
		val keys = keywordsToSpecs.keySet ++ newKeywords

		for (keyword <- keys) {
			keywordsToSpecs.updateWith(keyword)(specs => {
				specs match {
					case None => Some(Set(spec))
					case Some(existing) => {
						// Add or remove from existing sets
						val add = newKeywords.contains(keyword)
						val newSpecs = modifySet(add, existing, Set(spec))
						if (newSpecs.isEmpty) {
							None
						} else {
							Some(newSpecs)
						}
					}
				}
			})
		}
	}

	private def getKeywords(subject: IRI)(using vocab: CpmetaVocab)(using StatementSource): Set[String] = {
		Set.concat(
			StatementSource.getValues(subject, vocab.hasKeywords).flatMap(parseKeywords)*
		)
	}

	private def parseKeywords(obj: Value): Option[Set[String]] = {
		asStringLiteral(obj)
			.map(parseCommaSepList(_).toSet)
			.filter(_.nonEmpty)
	}

	// Retrieves or creates an ObjEntry,
	// and returns immutable view of it through ObjInfo interface.
	def getObjInfo(hash: Sha256Sum): ObjInfo = {
		getObjEntry(hash)
	}

	// Retrieves or creates a mutable ObjEntry
	private def getObjEntry(hash: Sha256Sum): ObjEntry = {
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

		inline def logWarn(warnPrefix: String): Unit =
			s"$warnPrefix value $key of property $prop on object ${objs(idx).hash.id}"

		if (isAssertion) {
			if (!bitmap(prop).add(key, idx)) {
				logWarn("Value already existed: asserted")
			}
		} else if (!bitmap(prop).remove(key, idx)) {
			logWarn("Value was not present: tried to retract")
		}
	}

	private def updateHasVarList(idx: Int, isAssertion: Boolean): Unit = {
		val hasVarsBm = mutableBoolBitmap(HasVarList)
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

	private def isSpec(iri: IRI): Boolean = {
		categMap(Spec).contains(iri)
	}

	private def registerSpec(spec: IRI) = {
		categMap(Spec).updateWith(spec) {
			case None => Some(emptyBitmap)
			case existing => existing
		}
	}

end IndexData

private def targetUri(obj: Value, isAssertion: Boolean) =
	if isAssertion && obj.isInstanceOf[IRI]
	then obj.asInstanceOf[IRI]
	else null

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

private def asStringLiteral(sl: Value): Option[String] = sl match
	case lit: Literal => asString(lit)
	case _ => None
