package se.lu.nateko.cp.meta.core.data

import java.time.Instant

final case class TimeInterval(start: Instant, stop: Instant)

final case class TemporalCoverage(interval: TimeInterval, resolution: Option[String])
