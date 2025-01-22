package se.lu.nateko.cp.meta.services.sparql.magic.index

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.AnyRefMap
import scala.collection.{IndexedSeq => IndSeq}

import org.eclipse.rdf4j.model.IRI
import org.roaringbitmap.buffer.MutableRoaringBitmap

import se.lu.nateko.cp.meta.services.sparql.index.*
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.ObjEntry
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap
import se.lu.nateko.cp.meta.core.algo.DatetimeHierarchicalBitmap
import se.lu.nateko.cp.meta.core.algo.DatetimeHierarchicalBitmap.DateTimeGeo
import se.lu.nateko.cp.meta.services.sparql.index.StringHierarchicalBitmap.StringGeo

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
}
