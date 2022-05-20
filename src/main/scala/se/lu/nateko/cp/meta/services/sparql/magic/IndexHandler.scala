package se.lu.nateko.cp.meta.services.sparql.magic

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.sail.Sail

import akka.actor.Scheduler
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.utils.async.throttle
import org.eclipse.rdf4j.sail.SailConnectionListener
import org.eclipse.rdf4j.sail.memory.MemoryStore
import akka.event.NoLogging
import akka.event.LoggingAdapter
import scala.concurrent.Future
import akka.Done

import java.io.ObjectOutputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.FileInputStream
import scala.util.Using
import java.nio.file.{Paths,Files}
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.IndexData

trait IndexProvider extends SailConnectionListener{
	def index: CpIndex
}

class IndexHandler(data: Option[IndexData], fromSail: Sail, scheduler: Scheduler, log: LoggingAdapter)(using ExecutionContext) extends IndexProvider {

	val index = data.fold(new CpIndex(fromSail)(log))(idx => new CpIndex(fromSail, idx)(log))
	index.flush()

	private val flushIndex: () => Unit = throttle(() => index.flush(), 1.second, scheduler)

	def statementAdded(s: Statement): Unit = {
		index.put(RdfUpdate(s, true))
		flushIndex()
	}

	def statementRemoved(s: Statement): Unit = {
		index.put(RdfUpdate(s, false))
		flushIndex()
	}

}

class DummyIndexProvider extends IndexProvider{
	val index = {
		val sail = new MemoryStore
		sail.init()
		new CpIndex(sail)(NoLogging)
	}
	def statementAdded(s: Statement): Unit = {}
	def statementRemoved(s: Statement): Unit = {}
}

object IndexHandler{
	import scala.concurrent.ExecutionContext.Implicits.global

	def storagePath = Paths.get("./sparqlMagicIndex.bin")

	def store(idx: CpIndex): Future[Done] = Future{

		Files.deleteIfExists(storagePath)

		Using(ObjectOutputStream(FileOutputStream(storagePath.toFile))){oos =>
			oos.writeObject(idx.serializableData)
			Done
		}.get
	}


	def restore(): Future[IndexData] = Future{
		Using(ObjectInputStream(FileInputStream(storagePath.toFile))){ois =>
			ois.readObject.asInstanceOf[IndexData]
		}.get
	}
}