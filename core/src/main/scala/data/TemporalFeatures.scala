package se.lu.nateko.cp.meta.core.data

import java.time.Instant

case class TimeInterval(start: Instant, stop: Instant)

case class TemporalCoverage(interval: TimeInterval, resolution: Option[String])
