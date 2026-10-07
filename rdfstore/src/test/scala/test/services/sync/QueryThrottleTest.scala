package se.lu.nateko.cp.meta.test.services.sync

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.sync.QueryThrottle

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class QueryThrottleTest extends AnyFunSpec:
	import ExecutionContext.Implicits.global

	private def millisOf(action: => Unit): Long =
		val start = System.nanoTime
		action
		(System.nanoTime - start) / 1000000

	describe("QueryThrottle"):

		it("never runs queries concurrently"):
			val throttle = QueryThrottle(0, 0.millis)
			val running = AtomicInteger(0)
			val maxRunning = AtomicInteger(0)
			val queries = (1 to 8).map: _ =>
				Future:
					throttle:
						maxRunning.accumulateAndGet(running.incrementAndGet(), (a, b) => Math.max(a, b))
						Thread.sleep(20)
						running.decrementAndGet()
			Await.result(Future.sequence(queries), 10.seconds)
			assert(maxRunning.get == 1)

		it("pauses at least the minimum pause between queries"):
			val throttle = QueryThrottle(0, 200.millis)
			assert(millisOf{throttle(()); throttle(())} >= 200)

		it("pauses in proportion to the duration of the previous query"):
			val throttle = QueryThrottle(3, 0.millis)
			assert(millisOf{throttle(Thread.sleep(100)); throttle(())} >= 100 + 300)

		it("pauses after failed queries, too"):
			val throttle = QueryThrottle(0, 200.millis)
			assert(millisOf{
				intercept[RuntimeException](throttle(throw RuntimeException("remote failure")))
				throttle(())
			} >= 200)

		it("does not pause before the first query"):
			assert(millisOf(QueryThrottle(10, 1.second)(())) < 500)
