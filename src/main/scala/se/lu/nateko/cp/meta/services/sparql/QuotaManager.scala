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
	private[this] val longRunning = new AtomicLong(-1) //only one long-running non-localhost query at a time

	def logNewQueryStart(clientIdOpt: Option[ClientId]): QueryQuotaManager = {
		val id = idGen.incrementAndGet()
		clientIdOpt.fold[QueryQuotaManager](new NoQuota(NoClient, id)){clientId =>
			val history: ClientHistory = q.getOrElseUpdate(clientId, TrieMap.empty)
			history += id -> QueryRun(now(), None, None)
			new ProperQueryQuotaManager(clientId, id)
		}
	}

	def quotaExcess(clientIdOpt: Option[ClientId]): Option[String] = clientIdOpt.flatMap(q.get).flatMap(hist => {
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

	private class ProperQueryQuotaManager(val cid: ClientId, val qid: QueryId) extends QueryQuotaManager{
		private def runInfo: Option[QueryRun] = q.get(cid).flatMap(_.get(qid))

		private def isOngoing = runInfo.fold(false)(_.isOngoing)
		private def streamingStarted = runInfo.fold(false)(_.streamingStarted)

		def keepRunningIndefinitely: Boolean = isOngoing && streamingStarted && longRunning.compareAndSet(-1, qid)

		def logQueryFinish(): Unit = {
			longRunning.compareAndSet(qid, -1)
			updateQueryRun{(run, time) => run.copy(stop = time)}
		}

		def logQueryStreamingStart(): Unit =
			updateQueryRun{(run, time) => run.copy(streamingStart = time)}

		private def updateQueryRun(update: (QueryRun, Option[Instant]) => QueryRun): Unit = synchronized{
			for(
				history <- q.get(cid);
				run <- history.get(qid)
			){
				history += qid -> update(run, Some(now()))
			}
		}
	}
}

object QuotaManager{

	type ClientId = String
	type QueryId = Long

	val NoClient: ClientId = "localhost"

	trait QueryQuotaManager{
		def cid: ClientId
		def qid: QueryId
		def keepRunningIndefinitely: Boolean
		def logQueryFinish(): Unit
		def logQueryStreamingStart(): Unit
	}

	class NoQuota(val cid: ClientId, val qid: QueryId) extends QueryQuotaManager{
		def keepRunningIndefinitely: Boolean = true
		def logQueryFinish(): Unit = {}
		def logQueryStreamingStart(): Unit = {}
	}

}
