package se.lu.nateko.cp.meta.services.sparql.magic

import akka.Done
import akka.actor.Scheduler
import akka.event.LoggingAdapter
import akka.event.NoLogging
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.KryoDataInput
import com.esotericsoftware.kryo.io.KryoDataOutput
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import com.esotericsoftware.kryo.serializers.DefaultArraySerializers.ByteArraySerializer
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
import java.lang.invoke.SerializedLambda
import java.nio.file.Files
import java.nio.file.Paths
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
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success

class IndexHandler(
	index: CpIndex,
	geo: Future[GeoIndex],
	scheduler: Scheduler,
	log: LoggingAdapter
)(using ExecutionContext) extends SailConnectionListener:

	//important that this is a val, not a def, otherwise throttle will work very wrongly
	private val flushIndex: () => Unit = throttle(() => index.flush(), 1.second, scheduler)

	// TODO handle changes for GeoIndex
	def statementAdded(s: Statement): Unit =
		// geo.onComplete:
		// 	case Success(geoIndex) => geoIndex.put()
		// 	case Failure(exception) => ???
		index.put(RdfUpdate(s, true))
		flushIndex()


	// TODO handle changes for GeoIndex
	def statementRemoved(s: Statement): Unit =
		index.put(RdfUpdate(s, false))
		flushIndex()


end IndexHandler

object IndexHandler{
	import scala.concurrent.ExecutionContext.Implicits.global

	def storagePath = Paths.get("./sparqlMagicIndex.bin")
	def dropStorage(): Unit = Files.deleteIfExists(storagePath)

	val kryo = Kryo()
	kryo.setRegistrationRequired(false)
	kryo.setReferences(true)
	kryo.register(classOf[Array[Object]])
	kryo.register(classOf[Class[?]])
	kryo.register(classOf[SerializedLambda])
	kryo.register(classOf[ClosureSerializer.Closure], new ClosureSerializer())
	kryo.register(classOf[IRI], IriSerializer)
	kryo.register(classOf[NativeIRI], IriSerializer)
	kryo.register(classOf[MemIRI], IriSerializer)
	kryo.register(classOf[Some[?]], OptionSomeSerializer)
	kryo.register(classOf[None.type], SingletonSerializer(None))
	kryo.register(classOf[HashMap[?,?]], HashmapSerializer)
	kryo.register(classOf[AnyRefMap[?,?]], AnyRefMapSerializer)
	kryo.register(classOf[Sha256Sum], Sha256HashSerializer)
	kryo.register(classOf[ObjEntry])
	kryo.register(classOf[StatKey])
	kryo.register(classOf[MutableRoaringBitmap], RoaringSerializer)
	Property.allConcrete.foreach{prop =>
		kryo.register(prop.getClass, SingletonSerializer(prop))
	}

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
			bitmap.serialize(new KryoDataOutput(output));

		override def read(kryo: Kryo, input: Input, tpe: Class[_ <: MutableRoaringBitmap]): MutableRoaringBitmap = {
			val bitmap = new MutableRoaringBitmap()
			bitmap.deserialize(new KryoDataInput(input))
			bitmap;
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

object OptionSomeSerializer extends Serializer[Some[?]]{
	override def write(kryo: Kryo, output: Output, some: Some[?]): Unit =
		kryo.writeClassAndObject(output, some.value)
	override def read(kryo: Kryo, input: Input, tpe: Class[? <: Some[?]]): Some[?] =
		Some(kryo.readClassAndObject(input))
}

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
