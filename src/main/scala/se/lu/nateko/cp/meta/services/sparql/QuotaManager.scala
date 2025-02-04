package se.lu.nateko.cp.meta.services.sparql

import se.lu.nateko.cp.meta.SparqlServerConfig

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentLinkedQueue, Executor}
import scala.collection.concurrent.TrieMap

class QuotaManager(config: SparqlServerConfig, executor: Executor)(implicit val now: () => Instant):
	import QuotaManager.*

	private val idGen = new AtomicLong(0)
	private val q = TrieMap.empty[ClientId, ClientHistory]
	private val longRunning = new AtomicLong(-1) //only one long-running non-localhost query at a time

	def getQueryQuotaManager(clientIdOpt: Option[ClientId]): QueryQuotaManager =
		val id = idGen.incrementAndGet()
		clientIdOpt.fold[QueryQuotaManager](new NoQuota(NoClient, id, executor)): clientId =>
			val history: ClientHistory = q.getOrElseUpdate(clientId, new ClientHistory)
			new ProperQueryQuotaManager(clientId, id)

	def quotaExcess(clientIdOpt: Option[ClientId]): Option[String] = clientIdOpt.flatMap(q.get).flatMap(hist => hist.synchronized:
		hist.banTo.collect:
			case to if now().compareTo(to) < 0 => s"You are banned for service overuse until $to"
		.orElse:

			var minuteCost: Float = 0
			var hourCost: Float = 0

			for((id, run) <- hist.runs)
				if run.isLastHour then
					val cost = run.cost
					hourCost += cost
					if(run.isLastMinute) minuteCost += cost
				else if !run.isOngoing then
					hist.runs -= id //forgetting old query runs

			if(hourCost >= config.quotaPerHour)
				Some("You have exceeded your hourly query quota. Please wait 1 hour to run more queries.")
			else if(minuteCost >= config.quotaPerMinute)
				Some("You have exceeded your per-minute query quota. Please wait 1 minute to run more queries.")
			else None
	)

	def cleanup(): Unit = for((cid, hist) <- q)
		for((qid, run) <- hist.runs)
			if(!run.isLastHour && !run.isOngoing || !run.isLastDay) hist.runs -= qid

		if(hist.runs.isEmpty && hist.queue.isEmpty) q -= cid

	case class QueryRun(start: Instant, streamingStart: Option[Instant], stop: Option[Instant]):

		def isOngoing = stop.isEmpty
		def streamingStarted = streamingStart.isDefined

		def cost: Float = streamingStart.orElse(stop).fold(Math.min(age, config.maxQueryRuntimeSec.toFloat))(ageAt)

		def isLastMinute: Boolean = age < 60
		def isLastHour: Boolean = age < 3600
		def isLastDay: Boolean = age < 3600 * 24

		private def age = ageAt(now())
		private def ageAt(i: Instant): Float = (i.toEpochMilli - start.toEpochMilli).toFloat / 1000

	private class ClientHistory:
		val runs = TrieMap.empty[QueryId, QueryRun]
		val queue = new ConcurrentLinkedQueue[Runnable]()
		var banTo: Option[Instant] = None
		def nRunning = runs.values.count(_.isOngoing) - queue.size

	private class ProperQueryQuotaManager(val cid: ClientId, val qid: QueryId) extends QueryQuotaManager:
		private def runInfo: Option[QueryRun] = q.get(cid).flatMap(_.runs.get(qid))

		private def isOngoing = runInfo.fold(false)(_.isOngoing)
		private def streamingStarted = runInfo.fold(false)(_.streamingStarted)

		def keepRunningIndefinitely: Boolean = isOngoing && streamingStarted && longRunning.compareAndSet(-1, qid)

		def logQueryFinish(): Unit =
			longRunning.compareAndSet(qid, -1)
			updateQueryRun{(run, time) => run.copy(stop = time)}
			advanceQueue()

		def logQueryStreamingStart(): Unit =
			updateQueryRun{(run, time) => run.copy(streamingStart = time)}

		def execute(r: Runnable): Unit =
			val hist = q(cid)
			hist.synchronized:
				if(hist.nRunning < config.maxParallelQueries && hist.queue.isEmpty)
					executor.execute(r)
				else
					hist.queue.add(r)

				if hist.queue.size > config.maxQueryQueue then
					val banTime = now().plus(config.banLength.toLong, ChronoUnit.MINUTES)

					hist.banTo = Some(banTime)
					hist.queue.clear()
				else
					hist.runs += qid -> QueryRun(now(), None, None)
					advanceQueue()

		private def advanceQueue(): Unit = for(hist <- q.get(cid)) hist.synchronized:
			if hist.nRunning < config.maxParallelQueries then
				val jobOrNull = hist.queue.poll()
				if(jobOrNull != null) executor.execute(jobOrNull)

		private def updateQueryRun(update: (QueryRun, Option[Instant]) => QueryRun): Unit =
			for(history <- q.get(cid)) history.synchronized:
				for(run <- history.runs.get(qid))
					history.runs += qid -> update(run, Some(now()))

	end ProperQueryQuotaManager

end QuotaManager

object QuotaManager:

	type ClientId = String
	type QueryId = Long

	val NoClient: ClientId = "localhost"

	trait QueryQuotaManager extends Executor:
		def cid: ClientId
		def qid: QueryId
		def keepRunningIndefinitely: Boolean
		def logQueryFinish(): Unit
		def logQueryStreamingStart(): Unit

	class NoQuota(val cid: ClientId, val qid: QueryId, inner: Executor) extends QueryQuotaManager:
		def keepRunningIndefinitely: Boolean = true
		def logQueryFinish(): Unit = {}
		def logQueryStreamingStart(): Unit = {}
		def execute(r: Runnable): Unit = inner.execute(r)

