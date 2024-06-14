package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.NotifyingSail
import org.eclipse.rdf4j.sail.helpers.NotifyingSailWrapper
import org.eclipse.rdf4j.sail.NotifyingSailConnection

class CpNotifyingSail(inner: NotifyingSail) extends NotifyingSailWrapper(inner):

	override def getConnection(): NotifyingSailConnection = ???
	def mkStore = new LmdbStore()

end CpNotifyingSail
