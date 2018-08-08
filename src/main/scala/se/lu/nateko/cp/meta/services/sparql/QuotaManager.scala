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
		history += id -> QueryRun(now(), None, None)
		new QueryQuotaManager(clientId, id)
	}

	def quotaExcess(clientId: ClientId): Option[String] = if(clientId.isEmpty) None else q.get(clientId).flatMap(hist => {
		var minuteCost: Float = 0
		var hourCost: Float = 0
		var count: Int = 0

		for((id, run) <- hist){
			if(run.isOngoing) count += 1
			if(run.isLastHour){
				val cost = run.cost
				hourCost += cost
				if(run.isLastMinute) minuteCost += cost
			} else if(!run.isOngoing){
				hist -= id //forgetting old query runs
			}
		}

		if(count > config.maxParallelQueries)
			Some(s"You have $count running queries. Please try again in a few seconds. Contact Carbon Portal if the problem persists.")
		else if(hourCost >= config.quotaPerHour)
			Some("You have exceeded your hourly query quota. Please wait 1 hour to run more queries.")
		else if(minuteCost >= config.quotaPerMinute)
			Some("You have exceeded your per-minute query quota. Please wait 1 minute to run more queries.")
		else None
	})

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
		private def ageAt(i: Instant): Float = (i.toEpochMilli - start.toEpochMilli).toFloat / 1000
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
