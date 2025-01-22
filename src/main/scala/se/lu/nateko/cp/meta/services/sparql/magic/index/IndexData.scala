package se.lu.nateko.cp.meta.services.sparql.magic.index

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.AnyRefMap
import scala.collection.{IndexedSeq => IndSeq}

import se.lu.nateko.cp.meta.utils.rdf4j.===
import org.eclipse.rdf4j.model.IRI
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

	def processTriple(subj: IRI, pred: IRI, obj: Value, vocab: CpmetaVocab, isAssertion: Boolean): Boolean = {
		import vocab.{hasObjectSpec, hasName}

		pred match {
			case `hasObjectSpec` =>
				obj match {
					case spec: IRI => {
						modForDobj(subj, idLookup, objs) { oe =>
							updateCategSet(categMap(Spec, categMaps), isAssertion, spec, oe.idx)
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

			case _ => false
		}
	}
}

private def categMap(
	prop: CategProp,
	categMaps: AnyRefMap[CategProp, AnyRefMap[?, MutableRoaringBitmap]]
): AnyRefMap[prop.ValueType, MutableRoaringBitmap] = categMaps
	.getOrElseUpdate(prop, new AnyRefMap[prop.ValueType, MutableRoaringBitmap])
	.asInstanceOf[AnyRefMap[prop.ValueType, MutableRoaringBitmap]]

private def updateCategSet[T <: AnyRef](
	set: AnyRefMap[T, MutableRoaringBitmap],
	isAssertion: Boolean,
	categ: T,
	idx: Int
): Unit = {
	val bm = set.getOrElseUpdate(categ, emptyBitmap)
	if isAssertion then bm.add(idx)
	else
		bm.remove(idx)
		if bm.isEmpty then {
			val _ = set.remove(categ)
		}
}

def getObjEntry(hash: Sha256Sum, idLookup: AnyRefMap[Sha256Sum, Int], objs: ArrayBuffer[ObjEntry]): ObjEntry = {
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

private def modForDobj[T](
	dobj: Value,
	idLookup: AnyRefMap[Sha256Sum, Int],
	objs: ArrayBuffer[ObjEntry]
)(mod: ObjEntry => T): Option[T] = dobj match {
	case CpVocab.DataObject(hash, prefix) =>
		val entry = getObjEntry(hash, idLookup, objs)
		if (entry.prefix == "") entry.prefix = prefix.intern()
		Some(mod(entry))

	case _ => None
}
