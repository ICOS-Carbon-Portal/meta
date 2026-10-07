package se.lu.nateko.cp.meta.services.sync

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
 * Runs queries one at a time, pausing after each of them for `pauseFactor` times as long as it took (but at least
 * `minPause`), so that the queried server is busy with them for at most 1 / (1 + pauseFactor) of the time.
 * The duration is measured on the client side, including network transfer, so it errs on the side of caution.
 */
final class QueryThrottle(pauseFactor: Double, minPause: FiniteDuration):
	require(pauseFactor >= 0, "pause factor must not be negative")

	private var nextAllowed: Long = 0 // System.nanoTime

	def apply[T](query: => T): T = synchronized:
		val wait = nextAllowed - System.nanoTime
		if wait > 0 then Thread.sleep(wait / 1000000, (wait % 1000000).toInt)
		val start = System.nanoTime
		try query
		finally
			val end = System.nanoTime
			val pause = Math.max((pauseFactor * (end - start)).toLong, minPause.toNanos)
			nextAllowed = end + pause

object QueryThrottle:
	val none = QueryThrottle(0, Duration.Zero)
