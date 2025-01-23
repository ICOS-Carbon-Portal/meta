package se.lu.nateko.cp.meta.services.sparql.magic.index

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.AnyRefMap
import scala.collection.{IndexedSeq => IndSeq}
import java.time.Instant

import se.lu.nateko.cp.meta.utils.rdf4j.{===, toJava, Rdf4jStatement}
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.roaringbitmap.buffer.MutableRoaringBitmap

import se.lu.nateko.cp.meta.services.sparql.index.*
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.ObjEntry
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap
import se.lu.nateko.cp.meta.core.algo.DatetimeHierarchicalBitmap
import se.lu.nateko.cp.meta.core.algo.DatetimeHierarchicalBitmap.DateTimeGeo
import se.lu.nateko.cp.meta.services.sparql.index.StringHierarchicalBitmap.StringGeo
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer.Hash
import org.eclipse.rdf4j.model.Value
import akka.event.LoggingAdapter
import se.lu.nateko.cp.meta.api.CloseableIterator
import org.eclipse.rdf4j.model.Statement

type StatementGetter = (subject: IRI | Null, predicate: IRI | Null, obj: Value | Null) => CloseableIterator[Statement]

final class DataStartGeo(objs: IndSeq[ObjEntry]) extends DateTimeGeo(objs(_).dataStart)
final class DataEndGeo(objs: IndSeq[ObjEntry]) extends DateTimeGeo(objs(_).dataEnd)
final class SubmStartGeo(objs: IndSeq[ObjEntry]) extends DateTimeGeo(objs(_).submissionStart)
final class SubmEndGeo(objs: IndSeq[ObjEntry]) extends DateTimeGeo(objs(_).submissionEnd)
final class FileNameGeo(objs: IndSeq[ObjEntry]) extends StringGeo(objs.apply(_).fName)

case class StatKey(spec: IRI, submitter: IRI, station: Option[IRI], site: Option[IRI])
case class StatEntry(key: StatKey, count: Int)

def emptyBitmap = MutableRoaringBitmap.bitmapOf()

class IndexData(nObjects: Int)(
	val objs: ArrayBuffer[ObjEntry] = new ArrayBuffer(nObjects),
	val idLookup: AnyRefMap[Sha256Sum, Int] = new AnyRefMap[Sha256Sum, Int](nObjects * 2),
	val boolMap: AnyRefMap[BoolProperty, MutableRoaringBitmap] = AnyRefMap.empty,
	val categMaps: AnyRefMap[CategProp, AnyRefMap[?, MutableRoaringBitmap]] = AnyRefMap.empty,
	val contMap: AnyRefMap[ContProp, HierarchicalBitmap[?]] = AnyRefMap.empty,
	val stats: AnyRefMap[StatKey, MutableRoaringBitmap] = AnyRefMap.empty,
	val initOk: MutableRoaringBitmap = emptyBitmap
) extends Serializable {
	def dataStartBm = DatetimeHierarchicalBitmap(DataStartGeo(objs))
	def dataEndBm = DatetimeHierarchicalBitmap(DataEndGeo(objs))
	def submStartBm = DatetimeHierarchicalBitmap(SubmStartGeo(objs))
	def submEndBm = DatetimeHierarchicalBitmap(SubmEndGeo(objs))
	def fileNameBm = StringHierarchicalBitmap(FileNameGeo(objs))

	def boolBitmap(prop: BoolProperty): MutableRoaringBitmap = boolMap.getOrElseUpdate(prop, emptyBitmap)

	def bitmap(prop: ContProp): HierarchicalBitmap[prop.ValueType] =
		contMap.getOrElseUpdate(
			prop,
			prop match {
				/** Important to maintain type consistency between props and HierarchicalBitmaps here*/
				case FileName        => fileNameBm
				case FileSize        => FileSizeHierarchicalBitmap(objs)
				case SamplingHeight  => SamplingHeightHierarchicalBitmap(objs)
				case DataStart       => dataStartBm
				case DataEnd         => dataEndBm
				case SubmissionStart => submStartBm
				case SubmissionEnd   => submEndBm
			}
		).asInstanceOf[HierarchicalBitmap[prop.ValueType]]

	def processTriple(log: LoggingAdapter)(
		subj: IRI,
		pred: IRI,
		obj: Value,
		vocab: CpmetaVocab,
		isAssertion: Boolean,
		getStatements: (subject: IRI | Null, predicate: IRI | Null, obj: Value | Null) => CloseableIterator[Statement],
		hasStatement: (IRI | Null, IRI | Null, Value | Null) => Boolean,
		nextVersCollIsComplete: (obj: IRI) => Boolean
	): Unit = {
		import vocab.*
		import vocab.prov.{wasAssociatedWith, startedAtTime, endedAtTime}

		val getObjEntry = objEntryGetter(idLookup, objs)
		val modForDobj = dobjModGetter(getObjEntry)

		pred match {
			case `hasObjectSpec` =>
				obj match {
					case spec: IRI => {
						val _ = modForDobj(subj) { oe =>
							updateCategSet(categMap(Spec, categMaps), spec, oe.idx, isAssertion)
							if (isAssertion) {
								if (oe.spec != null) removeStat(oe, stats, initOk)
								oe.spec = spec
								addStat(oe, stats, initOk)
							} else if (spec === oe.spec) {
								removeStat(oe, stats, initOk)
								oe.spec = null
							}
						}
					}

				}

			case `hasName` =>
				modForDobj(subj) { oe =>
					val fName = obj.stringValue
					if (isAssertion) oe.fName = fName
					else if (oe.fName == fName) oe.fileName == null
					handleContinuousPropUpdate(log)(FileName, fName, oe.idx, isAssertion)
				}

			case `wasAssociatedWith` =>
				subj match {
					case CpVocab.Submission(hash) =>
						val oe = getObjEntry(hash)
						removeStat(oe, stats, initOk)
						oe.submitter = targetUri(obj, isAssertion)
						if (isAssertion) { addStat(oe, stats, initOk) }
						obj match {
							case subm: IRI => updateCategSet(categMap(Submitter, categMaps), subm, oe.idx, isAssertion)
						}

					case CpVocab.Acquisition(hash) =>
						val oe = getObjEntry(hash)
						removeStat(oe, stats, initOk)
						oe.station = targetUri(obj, isAssertion)
						if (isAssertion) { addStat(oe, stats, initOk) }
						obj match {
							case stat: IRI => updateCategSet(categMap(Station, categMaps), Some(stat), oe.idx, isAssertion)
						}

					case _ =>
				}

			case `wasPerformedAt` => subj match {
					case CpVocab.Acquisition(hash) =>
						val oe = getObjEntry(hash)
						removeStat(oe, stats, initOk)
						oe.site = targetUri(obj, isAssertion)
						if (isAssertion) addStat(oe, stats, initOk)
						obj match {
							case site: IRI => updateCategSet(categMap(Site, categMaps), Some(site), oe.idx, isAssertion)
						}

					case _ =>
				}

			case `hasStartTime` =>
				ifDateTime(obj) { dt =>
					val _ = modForDobj(subj) { oe =>
						oe.dataStart = dt
						handleContinuousPropUpdate(log)(DataStart, dt, oe.idx, isAssertion)
					}
				}

			case `hasEndTime` =>
				ifDateTime(obj) { dt =>
					val _ = modForDobj(subj) { oe =>
						oe.dataEnd = dt
						handleContinuousPropUpdate(log)(DataEnd, dt, oe.idx, isAssertion)
					}
				}

			case `startedAtTime` =>
				ifDateTime(obj) { dt =>
					subj match {
						case CpVocab.Acquisition(hash) =>
							val oe = getObjEntry(hash)
							oe.dataStart = dt
							handleContinuousPropUpdate(log)(DataStart, dt, oe.idx, isAssertion)
						case CpVocab.Submission(hash) =>
							val oe = getObjEntry(hash)
							oe.submissionStart = dt
							handleContinuousPropUpdate(log)(SubmissionStart, dt, oe.idx, isAssertion)
						case _ =>
					}
				}

			case `endedAtTime` =>
				ifDateTime(obj) { dt =>
					subj match {
						case CpVocab.Acquisition(hash) =>
							val oe = getObjEntry(hash)
							oe.dataEnd = dt
							handleContinuousPropUpdate(log)(DataEnd, dt, oe.idx, isAssertion)
						case CpVocab.Submission(hash) =>
							val oe = getObjEntry(hash)
							oe.submissionEnd = dt
							handleContinuousPropUpdate(log)(SubmissionEnd, dt, oe.idx, isAssertion)
						case _ =>
					}
				}

			case `isNextVersionOf` =>
				val _ = modForDobj(obj) { oe =>
					val deprecated = boolBitmap(DeprecationFlag)
					if isAssertion then
						if !deprecated.contains(oe.idx) then // to prevent needless work
							val subjIsDobj = modForDobj(subj) { deprecator =>
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
												if nextVersCollIsComplete(subj)
												then deprecated.add(oe.idx)
											case _ =>
					else if
						deprecated.contains(oe.idx) && // this was to prevent needless repo access
						!hasStatement(null, isNextVersionOf, obj)
					then deprecated.remove(oe.idx)
				}

			case `hasSizeInBytes` =>
				ifLong(obj) { size =>
					val _ = modForDobj(subj) { oe =>
						inline def isRetraction = oe.size == size && !isAssertion
						if isAssertion then oe.size = size
						else if isRetraction then oe.size = -1

						if oe.isNextVersion then
							log.debug(s"Object ${oe.hash.id} appears to be a deprecator and just got fully uploaded. Will update the 'old' objects.")
							val deprecated = boolBitmap(DeprecationFlag)

							val directPrevVers: IndexedSeq[Int] =
								getStatements(subj, isNextVersionOf, null)
									.flatMap(st => dobjModGetter(getObjEntry)(st.getObject)(_.idx))
									.toIndexedSeq

							directPrevVers.foreach { oldIdx =>
								if isAssertion then deprecated.add(oldIdx)
								else if isRetraction then deprecated.remove(oldIdx)
								log.debug(s"Marked ${objs(oldIdx).hash.id} as ${if isRetraction then "non-" else ""}deprecated")
							}
							if directPrevVers.isEmpty then
								getIdxsOfPrevVersThroughColl(subj, vocab, getObjEntry, getStatements) match
									case None =>
										log.warning(s"Object ${oe.hash.id} is marked as a deprecator but has no associated old versions")
									case Some(throughColl) => if isAssertion then deprecated.add(throughColl)

						if (size >= 0) handleContinuousPropUpdate(log)(FileSize, size, oe.idx, isAssertion)
					}
				}

			case _ =>
		}
	}

	private def handleContinuousPropUpdate(log: LoggingAdapter)(
		prop: ContProp,
		key: prop.ValueType,
		idx: Int,
		isAssertion: Boolean
	): Unit = {
		def helpTxt = s"value $key of property $prop on object ${objs(idx).hash.base64Url}"
		if (isAssertion) {
			if (!bitmap(prop).add(key, idx)) {
				log.warning(s"Value already existed: asserted $helpTxt")
			}
		} else if (!bitmap(prop).remove(key, idx)) {
			log.warning(s"Value was not present: tried to retract $helpTxt")
		}
	}
}

private def getIdxsOfPrevVersThroughColl(
	deprecator: IRI,
	vocab: CpmetaVocab,
	getObjEntry: Sha256Sum => ObjEntry,
	getStatements: StatementGetter
): Option[Int] =
	getStatements(null, vocab.dcterms.hasPart, deprecator)
		.collect { case Rdf4jStatement(CpVocab.NextVersColl(oldHash), _, _) => getObjEntry(oldHash).idx }
		.toIndexedSeq
		.headOption

private def targetUri(obj: Value, isAssertion: Boolean) =
	if (isAssertion && obj.isInstanceOf[IRI]) {
		obj.asInstanceOf[IRI]
	} else { null }

private def categMap(
	prop: CategProp,
	categMaps: AnyRefMap[CategProp, AnyRefMap[?, MutableRoaringBitmap]]
): AnyRefMap[prop.ValueType, MutableRoaringBitmap] = categMaps
	.getOrElseUpdate(prop, new AnyRefMap[prop.ValueType, MutableRoaringBitmap])
	.asInstanceOf[AnyRefMap[prop.ValueType, MutableRoaringBitmap]]

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

def objEntryGetter(idLookup: AnyRefMap[Sha256Sum, Int], objs: ArrayBuffer[ObjEntry])(hash: Sha256Sum): ObjEntry = {
	idLookup.get(hash).fold {
		val canonicalHash = hash.truncate
		val oe = new ObjEntry(canonicalHash, objs.length, "")
		objs += oe
		idLookup += canonicalHash -> oe.idx
		oe
	}(objs.apply)
}

private def keyForDobj(obj: ObjEntry): Option[StatKey] =
	if obj.spec == null || obj.submitter == null then None
	else
		Some(
			StatKey(obj.spec, obj.submitter, Option(obj.station), Option(obj.site))
		)

private def addStat(
	obj: ObjEntry,
	stats: AnyRefMap[StatKey, MutableRoaringBitmap],
	initOk: MutableRoaringBitmap
): Unit = for (key <- keyForDobj(obj)) {
	stats.getOrElseUpdate(key, emptyBitmap).add(obj.idx)
	initOk.add(obj.idx)
}

private def removeStat(
	obj: ObjEntry,
	stats: AnyRefMap[StatKey, MutableRoaringBitmap],
	initOk: MutableRoaringBitmap
): Unit = for (key <- keyForDobj(obj)) {
	stats.get(key).foreach: bm =>
		bm.remove(obj.idx)
		if bm.isEmpty then { val _ = stats.remove(key) } // to prevent "orphan" URIs from lingering
	initOk.remove(obj.idx)
}

private def dobjModGetter[T](
	getObjEntry: (Sha256Sum => ObjEntry)
)(dobj: Value)(mod: ObjEntry => T): Option[T] =
	dobj match {
		case CpVocab.DataObject(hash, prefix) =>
			val entry = getObjEntry(hash)
			if (entry.prefix == "") entry.prefix = prefix.intern()
			Some(mod(entry))

		case _ => None
	}

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
