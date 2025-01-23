package se.lu.nateko.cp.meta.services.sparql.magic.index

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.AnyRefMap
import scala.collection.{IndexedSeq => IndSeq}
import java.time.Instant

import se.lu.nateko.cp.meta.utils.rdf4j.===
import se.lu.nateko.cp.meta.utils.*
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
import org.eclipse.rdf4j.model.Value
import akka.event.LoggingAdapter

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
		isAssertion: Boolean
	): Boolean = {
		import vocab.*
		import vocab.prov.{wasAssociatedWith, startedAtTime}

		val getObjEntry = objEntryGetter(idLookup, objs)
		val modForDobj = dobjModGetter(getObjEntry)

		pred match {
			case `hasObjectSpec` =>
				obj match {
					case spec: IRI => {
						modForDobj(subj) { oe =>
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

						true
					}

				}

			case `hasName` =>
				modForDobj(subj) { oe =>
					val fName = obj.stringValue
					if (isAssertion) oe.fName = fName
					else if (oe.fName == fName) oe.fileName == null
					handleContinuousPropUpdate(log)(FileName, fName, oe.idx, isAssertion)
				}
				true

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
						true

					case CpVocab.Acquisition(hash) =>
						val oe = getObjEntry(hash)
						removeStat(oe, stats, initOk)
						oe.station = targetUri(obj, isAssertion)
						if (isAssertion) { addStat(oe, stats, initOk) }
						obj match {
							case stat: IRI => updateCategSet(categMap(Station, categMaps), Some(stat), oe.idx, isAssertion)
						}
						true

					case _ => false
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
						true

					case _ => false
				}

			case `hasStartTime` =>
				ifDateTime(obj) { dt =>
					val _ = modForDobj(subj) { oe =>
						oe.dataStart = dt
						handleContinuousPropUpdate(log)(DataStart, dt, oe.idx, isAssertion)
					}
				}
				true

			case `hasEndTime` =>
				ifDateTime(obj) { dt =>
					val _ = modForDobj(subj) { oe =>
						oe.dataEnd = dt
						handleContinuousPropUpdate(log)(DataEnd, dt, oe.idx, isAssertion)
					}
				}
				true
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
				true

			case _ => false
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
		if bm.isEmpty then stats.remove(key) // to prevent "orphan" URIs from lingering
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
