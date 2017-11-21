package se.lu.nateko.cp.meta.services.sparql

import se.lu.nateko.cp.meta.SparqlServerConfig
import java.time.Instant
import scala.collection.concurrent.TrieMap
import java.util.concurrent.atomic.AtomicLong

class QuotaManager(config: SparqlServerConfig)(implicit val now: () => Instant) {
	import QuotaManager._

	private[this] val idGen = new AtomicLong(0)
	private[this] val q = TrieMap.empty[ClientId, ClientHistory]

	def logNewQueryStart(clientId: ClientId): QueryId = {
		val id = idGen.incrementAndGet()
		val history: ClientHistory = q.getOrElseUpdate(clientId, TrieMap.empty)
		history += id -> new QueryRun(now(), None)
		id
	}

	def logQueryFinish(clientId: ClientId, queryId: QueryId): Unit =
		for(
			history <- q.get(clientId);
			run <- history.get(queryId)
		){
			history += queryId -> new QueryRun(run.start, Some(now()))
		}

	def quotaExceeded(clientId: ClientId): Boolean = q.get(clientId).map(hist => {
		var minuteCost: Float = 0
		var hourCost: Float = 0
		var count: Int = 0

		for((id, run) <- hist){
			if(run.isLastHour){
				if(run.stop.isEmpty) count += 1
				val cost = run.cost
				hourCost += cost
				if(run.isLastMinute) minuteCost += cost
			} else {
				hist -= id //forgetting old query runs
			}
		}
		count >= config.maxParallelQueries ||
			minuteCost >= config.quotaPerMinute ||
			hourCost >= config.quotaPerHour
	}).getOrElse(false)

	def cleanup(): Unit = for((cid, hist) <- q){
		for((qid, run) <- hist)
			if(!run.isLastHour) hist -= qid

		if(hist.isEmpty) q -= cid
	}
}

object QuotaManager{

	class QueryRun(val start: Instant, val stop: Option[Instant])(implicit val now: () => Instant){

		def stopOrNow = stop.getOrElse(now())

		def cost: Float = (stopOrNow.toEpochMilli - start.toEpochMilli).toFloat / 1000

		def isLastMinute: Boolean = isLastXmillis(60000)
		def isLastHour: Boolean = isLastXmillis(3600000)

		private def isLastXmillis(millis: Long) = stop.fold(true)(s => now().toEpochMilli - start.toEpochMilli < millis)
	}

	type ClientId = String
	type QueryId = Long
	type ClientHistory = TrieMap[QueryId, QueryRun]
}
