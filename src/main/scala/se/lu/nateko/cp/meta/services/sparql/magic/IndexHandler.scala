package se.lu.nateko.cp.meta.services.sparql.magic

import akka.Done
import akka.actor.Scheduler
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.KryoDataInput
import com.esotericsoftware.kryo.io.KryoDataOutput
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.DefaultArraySerializers.ByteArraySerializer
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.impl.SimpleIRI
import org.eclipse.rdf4j.model.util.Values
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.SailConnectionListener
import org.eclipse.rdf4j.sail.memory.model.MemIRI
import org.eclipse.rdf4j.sail.nativerdf.model.NativeIRI
import org.roaringbitmap.buffer.MutableRoaringBitmap
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap.Geo
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.index.BoolProperty
import se.lu.nateko.cp.meta.services.sparql.index.CategProp
import se.lu.nateko.cp.meta.services.sparql.index.ContProp
import se.lu.nateko.cp.meta.services.sparql.index.FileSizeHierarchicalBitmap
import se.lu.nateko.cp.meta.services.sparql.index.FileSizeHierarchicalBitmap.LongGeo
import se.lu.nateko.cp.meta.services.sparql.index.Property
import se.lu.nateko.cp.meta.services.sparql.index.SamplingHeightHierarchicalBitmap
import se.lu.nateko.cp.meta.services.sparql.index.StringHierarchicalBitmap
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.DataEndGeo
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.DataStartGeo
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.FileNameGeo
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.IndexData
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.ObjEntry
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.SubmEndGeo
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.SubmStartGeo
import se.lu.nateko.cp.meta.utils.async.throttle
import se.lu.nateko.cp.meta.utils.rdf4j.===
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
import se.lu.nateko.cp.meta.utils.rdf4j.accessEagerly

import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import scala.collection.IndexedSeq
import scala.collection.mutable.AnyRefMap
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.jdk.OptionConverters.*
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success

class IndexHandler(scheduler: Scheduler)(using ExecutionContext):

	def getListener(
		sail: Sail,
		metaVocab: CpmetaVocab,
		index: CpIndex,
		geo: Future[(GeoIndex, GeoEventProducer)]
	) = new SailConnectionListener:
		//important that this is a val, not a def, otherwise throttle will work very wrongly
		private val flushIndex: () => Unit = throttle(() => index.flush(), 1.second, scheduler)

		def statementAdded(s: Statement): Unit =
			index.put(RdfUpdate(s, true))
			flushIndex()
			s match
				case Rdf4jStatement(dobj, pred, _) if pred === metaVocab.hasSizeInBytes =>
					geo.onComplete:
						case Success((geoIndex, events)) =>
							sail.accessEagerly:
								events.getDobjEvents(dobj).foreach: evs =>
									evs.foreach(geoIndex.put)
						case _ =>
				case _ =>

		def statementRemoved(s: Statement): Unit =
			index.put(RdfUpdate(s, false))
			flushIndex()


end IndexHandler

object IndexHandler{
	import scala.concurrent.ExecutionContext.Implicits.global
	//import com.esotericsoftware.minlog.Log
	//Log.DEBUG()

	def storagePath = Paths.get("./sparqlMagicIndex.bin")
	def dropStorage(): Unit = Files.deleteIfExists(storagePath)

	val kryo = Kryo()
	kryo.setRegistrationRequired(true)
	kryo.setReferences(false)
	kryo.register(classOf[Array[Object]])
	kryo.register(classOf[Array[Array[Object]]])
	kryo.register(classOf[Array[IRI]])
	kryo.register(classOf[Array[ObjEntry]])
	kryo.register(classOf[Array[String]])
	kryo.register(classOf[HashMap[?,?]], HashmapSerializer)
	kryo.register(classOf[AnyRefMap[?,?]], AnyRefMapSerializer)
	kryo.register(classOf[Sha256Sum], Sha256HashSerializer)
	kryo.register(classOf[StatKey], StatKeySerializer)
	kryo.register(classOf[MutableRoaringBitmap], RoaringSerializer)
	kryo.register(classOf[HierarchicalBitmap[?]], HierarchicalBitmapSerializer)
	kryo.register(classOf[IndexData], IndexDataSerializer)
	OrderingSerializer.register(kryo)
	OptionSerializer.register(kryo)

	Property.allConcrete.foreach: prop =>
		kryo.register(prop.getClass, SingletonSerializer(prop))

	def store(idx: CpIndex): Future[Done] = Future{
		dropStorage()
		val fos = FileOutputStream(storagePath.toFile)
		val gzos = new GZIPOutputStream(fos)
		storeToStream(idx, gzos)
	}.flatten

	def storeToStream(idx: CpIndex, os: OutputStream): Future[Done] = Future{
		val output = Output(os)
		kryo.writeObject(output, idx.serializableData)
		output.close()
		Done
	}.andThen{
		case Failure(_) => os.close()
	}

	def restore(): Future[IndexData] = Future{
		val fis = FileInputStream(storagePath.toFile)
		val is = new GZIPInputStream(fis)
		restoreFromStream(is).andThen{
			case _ => dropStorage()
		}
	}.flatten

	def restoreFromStream(is: InputStream): Future[IndexData] = Future{
		val input = Input(is)
		val data = kryo.readObject(input, classOf[IndexData])
		input.close()
		data
	}.andThen{
		case Failure(_) => is.close()
	}

}

object RoaringSerializer extends Serializer[MutableRoaringBitmap] {
	override def write(kryo: Kryo, output: Output, bitmap: MutableRoaringBitmap): Unit =
		bitmap.serialize(new KryoDataOutput(output))

	override def read(kryo: Kryo, input: Input, tpe: Class[_ <: MutableRoaringBitmap]): MutableRoaringBitmap = {
		val bitmap = new MutableRoaringBitmap()
		bitmap.deserialize(new KryoDataInput(input))
		bitmap
	}
}

object Sha256HashSerializer extends Serializer[Sha256Sum] {
	private val arrSer: Serializer[Array[Byte]] = new ByteArraySerializer()

	override def write(kryo: Kryo, output: Output, hash: Sha256Sum): Unit =
		kryo.writeObject(output, hash.getBytes.toArray, arrSer)

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: Sha256Sum]): Sha256Sum = {
		val bytes = kryo.readObject(input, classOf[Array[Byte]], arrSer)
		new Sha256Sum(bytes)
	}
}

object IndexDataSerializer extends Serializer[IndexData]:
	override def write(kryo: Kryo, output: Output, data: IndexData): Unit =
		output.writeInt(data.objs.size)

		registerIriSerializer(kryo, IriSerializer)

		val iriIndex = buildIriIndex(data.objs)
		kryo.writeObject(output, iriIndex)

		val prefixIndex = buildPrefixIndex(data.objs)
		kryo.writeObject(output, prefixIndex)

		val iriWriteIndex = iriIndex.zipWithIndex.toMap
		registerIriSerializer(kryo, IndexedIriWriter(iriWriteIndex))

		kryo.register(classOf[ObjEntry], ObjEntrySerializer(prefixIndex))
		GeoSerializer.register(kryo, data.objs)

		kryo.writeObject(output, data.objs.toArray)
		kryo.writeObject(output, data.stats)
		kryo.writeObject(output, data.boolMap)
		kryo.writeObject(output, data.categMaps)
		kryo.writeObject(output, data.contMap)
		kryo.writeObject(output, data.initOk)

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: IndexData]): IndexData =
		val nObjs = input.readInt()

		def readObj[T](cls: Class[T]): T = kryo.readObject[T](input, cls)

		registerIriSerializer(kryo, IriSerializer)
		val iriIndex = readObj(classOf[Array[IRI]])
		registerIriSerializer(kryo, IndexedIriReader(iriIndex))

		val prefixIndex = readObj(classOf[Array[String]])
		kryo.register(classOf[ObjEntry], ObjEntrySerializer(prefixIndex))

		val objs = readObj(classOf[Array[ObjEntry]])

		GeoSerializer.register(kryo, objs)

		IndexData(nObjs)(
			objs = ArrayBuffer.from(objs),
			idLookup = AnyRefMap.from(objs.indices.iterator.map(oidx => objs(oidx).hash -> oidx)),
			stats = readObj(classOf[AnyRefMap[StatKey, MutableRoaringBitmap]]),
			boolMap = readObj(classOf[AnyRefMap[BoolProperty, MutableRoaringBitmap]]),
			categMaps = readObj(classOf[AnyRefMap[CategProp, AnyRefMap[?, MutableRoaringBitmap]]]),
			contMap = readObj(classOf[AnyRefMap[ContProp, HierarchicalBitmap[?]]]),
			initOk = readObj(classOf[MutableRoaringBitmap])
		)

	private def registerIriSerializer(kryo: Kryo, serializer: Serializer[IRI]): Unit =
		kryo.register(classOf[IRI], serializer)
		kryo.register(classOf[NativeIRI], serializer)
		kryo.register(classOf[MemIRI], serializer)
		kryo.register(classOf[SimpleIRI], serializer)

	private def buildPrefixIndex(objs: ArrayBuffer[ObjEntry]): Array[String] = objs
		.iterator
		.map(_.prefix)
		.distinct
		.toArray

	private def buildIriIndex(objs: ArrayBuffer[ObjEntry]): Array[IRI] = objs
		.iterator
		.flatMap: o =>
			Iterator(o.spec, o.submitter, o.station, o.site)
		.filter(_ != null)
		.distinct
		.toArray

end IndexDataSerializer

class ObjEntrySerializer(prefixIndex: IndexedSeq[String]) extends Serializer[ObjEntry]:

	override def write(kryo: Kryo, output: Output, obj: ObjEntry): Unit =
		kryo.writeObject(output, obj.hash)
		output.writeInt(obj.idx)

		output.writeInt(prefixIndex.indexOf(obj.prefix))

		kryo.writeObjectOrNull(output, obj.spec, classOf[IRI])
		kryo.writeObjectOrNull(output, obj.submitter, classOf[IRI])
		kryo.writeObjectOrNull(output, obj.station, classOf[IRI])
		kryo.writeObjectOrNull(output, obj.site, classOf[IRI])

		output.writeLong(obj.size)
		output.writeString(obj.fName)
		output.writeFloat(obj.samplingHeight)
		output.writeLong(obj.dataStart)
		output.writeLong(obj.dataEnd)
		output.writeLong(obj.submissionStart)
		output.writeLong(obj.submissionEnd)
		output.writeBoolean(obj.isNextVersion)

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: ObjEntry]): ObjEntry =
		val objEntry = ObjEntry(
			hash = kryo.readObject[Sha256Sum](input, classOf[Sha256Sum]),
			idx = input.readInt(),
			prefix = prefixIndex(input.readInt())
		)

		objEntry.spec = kryo.readObjectOrNull(input, classOf[IRI])
		objEntry.submitter = kryo.readObjectOrNull(input, classOf[IRI])
		objEntry.station = kryo.readObjectOrNull(input, classOf[IRI])
		objEntry.site = kryo.readObjectOrNull(input, classOf[IRI])

		objEntry.size = input.readLong()
		objEntry.fName = input.readString()
		objEntry.samplingHeight = input.readFloat()
		objEntry.dataStart = input.readLong()
		objEntry.dataEnd = input.readLong()
		objEntry.submissionStart = input.readLong()
		objEntry.submissionEnd = input.readLong()
		objEntry.isNextVersion = input.readBoolean()

		objEntry

end ObjEntrySerializer

object HierarchicalBitmapSerializer extends Serializer[HierarchicalBitmap[?]]:

	override def write(kryo: Kryo, output: Output, bitmap: HierarchicalBitmap[?]): Unit =
		bitmap.serialize(new KryoDataOutput(output), kryo.writeObject(output, _))

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: HierarchicalBitmap[?]]): HierarchicalBitmap[?] =
		HierarchicalBitmap.deserialize(
			new KryoDataInput(input),
			[T] => (cls: Class[T]) => kryo.readObject(input, cls)
		)


object IriSerializer extends Serializer[IRI]:
	override def write(kryo: Kryo, output: Output, iri: IRI): Unit =
		output.writeString(iri.stringValue())

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: IRI]): IRI =
		Values.iri(input.readString())

class IndexedIriWriter(index: Map[IRI, Int]) extends Serializer[IRI]:
	override def read(kryo: Kryo, input: Input, tpe: Class[? <: IRI]): IRI = ???
	override def write(kryo: Kryo, output: Output, iri: IRI): Unit =
		val idx = if iri == null then -1 else index(iri)
		output.writeInt(idx)

class IndexedIriReader(index: IndexedSeq[IRI]) extends Serializer[IRI]:
	override def write(kryo: Kryo, output: Output, iri: IRI): Unit = ???
	override def read(kryo: Kryo, input: Input, tpe: Class[? <: IRI]): IRI =
		val idx = input.readInt()
		if idx < 0 then null else index(idx)


class MapSerializer[T <: collection.Map[?,?]](buildr: IterableOnce[(Object,Object)] => T) extends Serializer[T]{
	override def write(kryo: Kryo, output: Output, m: T): Unit =
		kryo.writeObject(output, m.iterator.map(_.toArray).toArray)

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: T]): T = {
		val nested = kryo.readObject(input, classOf[Array[Array[Object]]])
		buildr(nested.iterator.map{arr => arr(0) -> arr(1)})
	}
}

object HashmapSerializer extends MapSerializer[HashMap[?,?]](HashMap.from)
object AnyRefMapSerializer extends MapSerializer[AnyRefMap[?,?]](AnyRefMap.from)

class SingletonSerializer[T <: Singleton](s: T) extends Serializer[T]{
	override def write(kryo: Kryo, output: Output, s: T): Unit = {}
	override def read(kryo: Kryo, input: Input, tpe: Class[? <: T]): T = s
}

object GeoSerializer:
	def register(kryo: Kryo, objs: IndexedSeq[ObjEntry]): Unit =
		val geos = HashMap.empty[Int, Geo[?]]
		val ser = GeoSerializer(geos)
		def regGeo(geo: Geo[?]): Unit =
			val reg = kryo.register(geo.getClass, ser)
			geos += reg.getId -> geo

		regGeo(DataStartGeo(objs))
		regGeo(DataEndGeo(objs))
		regGeo(SubmStartGeo(objs))
		regGeo(SubmEndGeo(objs))
		regGeo(FileSizeHierarchicalBitmap.LongGeo(objs))
		regGeo(FileNameGeo(objs))
		regGeo(SamplingHeightHierarchicalBitmap.SamplingHeightGeo(objs))
		kryo.register(classOf[HierarchicalBitmap.Geo[?]], ser)

class GeoSerializer private(geos: HashMap[Int, Geo[?]]) extends Serializer[Geo[?]]:

	override def write(kryo: Kryo, output: Output, geo: Geo[?]): Unit =
		kryo.writeClass(output, geo.getClass)

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: Geo[?]]): Geo[?] =
		val geoReg = kryo.readClass(input)
		geos.getOrElse(geoReg.getId, throw IllegalArgumentException(s"Unknown Geo class: $geoReg"))

object OrderingSerializer:
	def register(kryo: Kryo): Unit =
		val ords = HashMap.empty[Int, Ordering[?]]
		val ser = OrderingSerializer(ords)
		def regOrd(ord: Ordering[?]): Unit =
			val reg = kryo.register(ord.getClass, ser)
			ords += reg.getId -> ord

		regOrd(scala.math.Ordering.Long)
		regOrd(StringHierarchicalBitmap.StringOrdering)
		regOrd(scala.math.Ordering.Float.IeeeOrdering)
		kryo.register(classOf[scala.math.Ordering[?]], ser)

class OrderingSerializer private(ords: HashMap[Int, Ordering[?]]) extends Serializer[Ordering[?]]:

	override def write(kryo: Kryo, output: Output, ord: Ordering[?]): Unit =
		kryo.writeClass(output, ord.getClass)

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: Ordering[?]]): Ordering[?] =
		val ordReg = kryo.readClass(input)
		ords.getOrElse(ordReg.getId, throw IllegalArgumentException(s"Unknown Ordering class: $ordReg"))

object OptionSerializer extends Serializer[Option[?]]:

	def register(kryo: Kryo) =
		kryo.register(classOf[Option[?]], OptionSerializer)
		kryo.register(classOf[Some[?]], OptionSerializer)
		kryo.register(classOf[None.type], OptionSerializer)

	override def write(kryo: Kryo, output: Output, option: Option[?]): Unit =
		output.writeBoolean(option.isDefined)
		for v <- option do
			kryo.writeClassAndObject(output, v)

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: Option[?]]): Option[?] =
		if input.readBoolean() then
			Option(kryo.readClassAndObject(input))
		else None


object StatKeySerializer extends Serializer[StatKey]:

	override def write(kryo: Kryo, output: Output, key: StatKey): Unit =
		kryo.writeObject(output, key.spec)
		kryo.writeObject(output, key.submitter)
		kryo.writeObjectOrNull(output, key.site.getOrElse(null), classOf[IRI])
		kryo.writeObjectOrNull(output, key.station.getOrElse(null), classOf[IRI])

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: StatKey]): StatKey =
		StatKey(
			spec = kryo.readObject(input, classOf[IRI]),
			submitter = kryo.readObject(input, classOf[IRI]),
			site = Option(kryo.readObjectOrNull(input, classOf[IRI])),
			station = Option(kryo.readObjectOrNull(input, classOf[IRI]))
		)
