package se.lu.nateko.cp.meta.services.sparql.magic

import akka.Done
import akka.actor.Scheduler
import akka.event.NoLogging
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.KryoDataInput
import com.esotericsoftware.kryo.io.KryoDataOutput
import com.esotericsoftware.kryo.io.Output
// import com.esotericsoftware.kryo.serializers.ClosureSerializer
import com.esotericsoftware.kryo.serializers.DefaultArraySerializers.ByteArraySerializer
import com.esotericsoftware.kryo.serializers.OptionalSerializers.OptionalSerializer
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.util.Values
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.SailConnectionListener
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.eclipse.rdf4j.sail.nativerdf.model.NativeIRI
import org.roaringbitmap.buffer.MutableRoaringBitmap
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.IndexData
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.ObjEntry
import se.lu.nateko.cp.meta.utils.async.throttle

import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
// import java.lang.invoke.SerializedLambda
import java.nio.file.Files
import java.nio.file.Paths
import scala.collection.IndexedSeq
import scala.collection.mutable.AnyRefMap
import scala.collection.mutable.HashMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Using
import java.io.OutputStream
import java.io.InputStream
import org.eclipse.rdf4j.sail.memory.model.MemIRI
import se.lu.nateko.cp.meta.services.sparql.index.Property
import se.lu.nateko.cp.meta.services.sparql.index.BoolProperty
import se.lu.nateko.cp.meta.services.sparql.index.CategProp
import se.lu.nateko.cp.meta.services.sparql.index.ContProp
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.===
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
import se.lu.nateko.cp.meta.utils.rdf4j.accessEagerly
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.DataStartGeo
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.DataEndGeo
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.FileNameGeo
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.SubmStartGeo
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.SubmEndGeo
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap.Coord
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap.Geo
import se.lu.nateko.cp.meta.core.algo.DatetimeHierarchicalBitmap
import scala.collection.mutable.ArrayBuffer
import se.lu.nateko.cp.meta.services.sparql.index.FileSizeHierarchicalBitmap.LongGeo
import se.lu.nateko.cp.meta.services.sparql.index.FileSizeHierarchicalBitmap
import se.lu.nateko.cp.meta.services.sparql.index.SamplingHeightHierarchicalBitmap
import scala.jdk.OptionConverters.*
import scala.jdk.javaapi.OptionConverters.*
import se.lu.nateko.cp.meta.utils.asOptInstanceOf
import se.lu.nateko.cp.meta.services.sparql.index.StringHierarchicalBitmap


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

	def storagePath = Paths.get("./sparqlMagicIndex.bin")
	def dropStorage(): Unit = Files.deleteIfExists(storagePath)

	val kryo = Kryo()
	kryo.setRegistrationRequired(true)
	kryo.setReferences(false)
	kryo.register(classOf[Array[Object]])
	// kryo.register(classOf[Class[?]])
	// kryo.register(classOf[SerializedLambda])
	// kryo.register(classOf[ClosureSerializer.Closure], new ClosureSerializer())
	kryo.register(classOf[IRI], IriSerializer)
	kryo.register(classOf[NativeIRI], IriSerializer)
	kryo.register(classOf[MemIRI], IriSerializer)
	kryo.register(classOf[None.type], SingletonSerializer(None))
	kryo.register(classOf[HashMap[?,?]], HashmapSerializer)
	kryo.register(classOf[AnyRefMap[?,?]], AnyRefMapSerializer)
	kryo.register(classOf[Sha256Sum], Sha256HashSerializer)
	kryo.register(classOf[ObjEntry])
	kryo.register(classOf[StatKey])
	kryo.register(classOf[MutableRoaringBitmap], RoaringSerializer)
	kryo.register(classOf[Property])
	kryo.register(classOf[BoolProperty])
	kryo.register(classOf[Array[Array[Any]]])
	kryo.register(classOf[HierarchicalBitmap[?]], HierarchicalBitmapSerializer)
	kryo.register(classOf[BoolProperty])
	kryo.register(classOf[IndexData], IndexDataSerializer)
	kryo.register(classOf[ArrayBuffer[_]])
	kryo.register(classOf[scala.math.Ordering.Long.type])
	kryo.register(classOf[StringHierarchicalBitmap.StringOrdering.type])
	kryo.register(classOf[SamplingHeightHierarchicalBitmap.SamplingHeightGeo])
	kryo.register(classOf[scala.math.Ordering.Float.IeeeOrdering.type])
	kryo.register(classOf[Option[_]], OptionSerializer)
	kryo.register(classOf[Some[_]], OptionSerializer)
	Property.allConcrete.foreach{prop =>
		kryo.register(prop.getClass, SingletonSerializer(prop))
	}

	// HierarchicalBitmap.Geo[Float].allConcrete.foreach: g =>
	// 	kryo.register(g.getClass, SingletonSerializer(g))

	def store(idx: CpIndex): Future[Done] = Future{
		dropStorage()
		storeToStream(idx, FileOutputStream(storagePath.toFile))
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
		val is = FileInputStream(storagePath.toFile)
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
		kryo.register(classOf[HierarchicalBitmap.Geo[?]], GeoSerializer(data.objs))
		output.writeInt(data.objs.size)
		kryo.writeObject(output, data.objs)
		kryo.writeObject(output, data.idLookup)
		kryo.writeObject(output, data.stats)
		kryo.writeObject(output, data.boolMap)
		kryo.writeObject(output, data.categMaps)
		kryo.writeObject(output, data.contMap)
		kryo.writeObject(output, data.initOk)

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: IndexData]): IndexData =
		//TODO Get rid of the type cast below. Really should not be needed.
		def readObj[T](using ct: ClassTag[T]): T = kryo.readObject[T](input, ct.runtimeClass.asInstanceOf[Class[T]])
		val nObjs = input.readInt()
		val objs = readObj[ArrayBuffer[ObjEntry]]
		kryo.register(classOf[HierarchicalBitmap.Geo[?]], GeoSerializer(objs))
		IndexData(nObjs)(
			objs = objs,
			idLookup = readObj[AnyRefMap[Sha256Sum, Int]],
			stats = readObj[AnyRefMap[StatKey, MutableRoaringBitmap]],
			boolMap = readObj[AnyRefMap[BoolProperty, MutableRoaringBitmap]],
			categMaps = readObj[AnyRefMap[CategProp, AnyRefMap[?, MutableRoaringBitmap]]],
			contMap = readObj[AnyRefMap[ContProp, HierarchicalBitmap[?]]],
			initOk = readObj[MutableRoaringBitmap]
		)


object HierarchicalBitmapSerializer extends Serializer[HierarchicalBitmap[?]]:

	override def write(kryo: Kryo, output: Output, bitmap: HierarchicalBitmap[?]): Unit =
		bitmap.serialize(new KryoDataOutput(output), kryo.writeObject(output, _))

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: HierarchicalBitmap[?]]): HierarchicalBitmap[?] =
		HierarchicalBitmap.deserialize(new KryoDataInput(input), [T] => (cls: Class[T]) =>
			kryo.readObject(input, cls))
			// val concreteClass = cls.runtimeClass
			// kryo.readClassAndObject(input).asInstanceOf[concreteClass])


object IriSerializer extends Serializer[IRI]{
	override def write(kryo: Kryo, output: Output, iri: IRI): Unit =
		kryo.writeObject(output, iri.stringValue)

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: IRI]): IRI =
		Values.iri(kryo.readObject(input, classOf[String]))
}


class MapSerializer[T <: collection.Map[?,?]](buildr: IterableOnce[(Object,Object)] => T) extends Serializer[T]{
	override def write(kryo: Kryo, output: Output, m: T): Unit =
		kryo.writeObject(output, m.iterator.map(_.toArray).toArray)

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: T]): T = {
		val nested = kryo.readObject[Array[Array[Object]]](input, classOf[Array[Array[Object]]])
		buildr(nested.iterator.map{arr => arr(0) -> arr(1)})
	}
}

object HashmapSerializer extends MapSerializer[HashMap[?,?]](HashMap.from)
object AnyRefMapSerializer extends MapSerializer[AnyRefMap[?,?]](AnyRefMap.from)

class SingletonSerializer[T <: Singleton](s: T) extends Serializer[T]{
	override def write(kryo: Kryo, output: Output, s: T): Unit = {}
	override def read(kryo: Kryo, input: Input, tpe: Class[? <: T]): T = s
}

object OptionSerializer extends Serializer[Option[?]]:

	override def write(kryo: Kryo, output: Output, option: Option[?]): Unit =
		kryo.writeClassAndObject(output, option.getOrElse(null))

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: Option[?]]): Option[?] =
		Option(kryo.readClassAndObject(input))

class GeoSerializer(objs: IndexedSeq[ObjEntry]) extends Serializer[Geo[?]]:
	override def write(kryo: Kryo, output: Output, geo: Geo[?]): Unit =
		geo match
			case dateTimeLG: DataStartGeo =>
				output.writeString("DataStartGeo")
			case dateTimeLG: DataEndGeo =>
				output.writeString("DataEndGeo")
			case dateTimeLG: SubmStartGeo =>
				output.writeString("SubmStartGeo")
			case dateTimeLG: SubmEndGeo =>
				output.writeString("SubmEndGeo")
			case fileSizeLongGeo: FileSizeHierarchicalBitmap.LongGeo =>
				output.writeString("FileSizeGeo")
			case gs: FileNameGeo =>
				output.writeString("FileNameGeo")
			case gf: SamplingHeightHierarchicalBitmap.SamplingHeightGeo =>
				output.writeString("SamplingHeightGeo")
			case _ => throw IllegalArgumentException("Unknown geo type")

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: Geo[?]]): Geo[?] =
		input.readString() match
			case "DataStartGeo" => DataStartGeo(objs)
			case "DataEndGeo"   => DataEndGeo(objs)
			case "SubmStartGeo" => SubmStartGeo(objs)
			case "SubmEndGeo"   => SubmEndGeo(objs)
			case "FileSizeGeo"  => FileSizeHierarchicalBitmap.LongGeo(objs)
			case "FileNameGeo"  => FileNameGeo(objs)
			case "SamplingHeightGeo" =>
				SamplingHeightHierarchicalBitmap.SamplingHeightGeo(objs)
			case other => throw new IllegalArgumentException(s"Unknown Geo type: $other")

// object OptionShortSerializer extends Serializer[Option[Short]]:

// 	override def write(kryo: Kryo, output: Output, option: Option[Short]): Unit =
// 		output.writeBoolean(option.isDefined)
// 		for short <- option do output.writeShort(short)

// 	override def read(kryo: Kryo, input: Input, tpe: Class[? <: Option[Short]]): Option[Short] =
// 		if input.readBoolean() then Some(input.readShort()) else None
