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

class IndexHandler(index: CpIndex, scheduler: Scheduler, log: LoggingAdapter)(using ExecutionContext) extends SailConnectionListener {

	private def flushIndex(): Unit = throttle(() => index.flush(), 1.second, scheduler)

	def statementAdded(s: Statement): Unit = {
		index.put(RdfUpdate(s, true))
		flushIndex()
	}

	def statementRemoved(s: Statement): Unit = {
		index.put(RdfUpdate(s, false))
		flushIndex()
	}

}

object IndexHandler{
	import scala.concurrent.ExecutionContext.Implicits.global

	val kryo = Kryo()
	kryo.setRegistrationRequired(false)
	kryo.setReferences(true)
	kryo.register(classOf[Array[Object]])
	kryo.register(classOf[Class[?]])
	kryo.register(classOf[SerializedLambda])
	kryo.register(classOf[ClosureSerializer.Closure], new ClosureSerializer())
	kryo.register(classOf[IRI], IriSerializer)
	kryo.register(classOf[NativeIRI], IriSerializer)
	kryo.register(classOf[Some[?]], OptionSomeSerializer)
	kryo.register(classOf[HashMap[?,?]], HashmapSerializer)
	kryo.register(classOf[Sha256Sum], Sha256HashSerializer)
	kryo.register(classOf[ObjEntry])
	kryo.register(classOf[StatKey])
	kryo.register(classOf[MutableRoaringBitmap], RoaringSerializer)

	def storagePath = Paths.get("./sparqlMagicIndex.bin")

	def store(idx: CpIndex): Future[Done] = Future{

		dropStorage()

		Using(Output(FileOutputStream(storagePath.toFile))){output =>
			kryo.writeObject(output, idx.serializableData)
			Done
		}.get
	}

	def dropStorage(): Unit = Files.deleteIfExists(storagePath)

	def restore(): Future[IndexData] = Future{
		Using(Input(FileInputStream(storagePath.toFile))){input =>
			kryo.readObject(input, classOf[IndexData])
		}.get
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

object HashmapSerializer extends Serializer[HashMap[?,?]]{
	override def write(kryo: Kryo, output: Output, hmap: HashMap[?,?]): Unit =
		kryo.writeObject(output, hmap.iterator.map(_.toArray).toArray)

	override def read(kryo: Kryo, input: Input, tpe: Class[? <: HashMap[?,?]]): HashMap[?,?] = {
		val nested = kryo.readObject[Array[Array[Object]]](input, classOf[Array[Array[Object]]])
		HashMap.from(nested.iterator.map{arr => arr(0) -> arr(1)})
	}
}