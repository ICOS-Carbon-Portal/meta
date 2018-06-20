package se.lu.nateko.cp.meta.services.sparql

import se.lu.nateko.cp.meta.SparqlServerConfig
import java.time.Instant
import scala.collection.concurrent.TrieMap
import java.util.concurrent.atomic.AtomicLong

class QuotaManager(config: SparqlServerConfig)(implicit val now: () => Instant) {
	import QuotaManager._

	private[this] val idGen = new AtomicLong(0)
	private type ClientHistory = TrieMap[QueryId, QueryRun]
	private[this] val q = TrieMap.empty[ClientId, ClientHistory]

	def logNewQueryStart(clientId: ClientId): QueryQuotaManager = {
		val id = idGen.incrementAndGet()
		val history: ClientHistory = q.getOrElseUpdate(clientId, TrieMap.empty)
		val nownow = now()
		history += id -> QueryRun(nownow, None, None)
		new QueryQuotaManager(clientId, id)
	}

	def quotaExceeded(clientId: ClientId): Boolean = if(clientId.isEmpty) false else q.get(clientId).map(hist => {
		var minuteCost: Float = 0
		var hourCost: Float = 0
		var count: Int = 0

		for((id, run) <- hist){
			if(run.isLastHour){
				if(run.isOngoing) count += 1
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
			if(!run.isLastHour && !run.isOngoing || !run.isLastDay) hist -= qid

		if(hist.isEmpty) q -= cid
	}

	case class QueryRun(start: Instant, streamingStart: Option[Instant], stop: Option[Instant]){

		def isOngoing = stop.isEmpty
		def streamingStarted = streamingStart.isDefined

		def cost: Float = streamingStart.orElse(stop).fold(Math.min(age, config.maxQueryRuntimeSec.toFloat))(ageAt)

		def isLastMinute: Boolean = age < 60
		def isLastHour: Boolean = age < 3600
		def isLastDay: Boolean = age < 3600 * 24

		private def age = ageAt(now())
		private def ageAt(i: Instant): Float = (start.toEpochMilli - i.toEpochMilli).toFloat / 1000
	}

	class QueryQuotaManager(cid: ClientId, qid: QueryId){
		private def runInfo = q.get(cid).flatMap(_.get(qid))

		def isOngoing = runInfo.fold(false)(_.isOngoing)
		def streamingStarted = runInfo.fold(false)(_.streamingStarted)

		def logQueryFinish(): Unit =
			updateQueryRun{(run, time) => run.copy(stop = time)}

		def logQueryStreamingStart(): Unit =
			updateQueryRun{(run, time) => run.copy(streamingStart = time)}

		private def updateQueryRun(update: (QueryRun, Option[Instant]) => QueryRun): Unit =
			for(
				history <- q.get(cid);
				run <- history.get(qid)
			){
				history += qid -> update(run, Some(now()))
			}
	}
}

object QuotaManager{
	type ClientId = Option[String]
	type QueryId = Long
}
