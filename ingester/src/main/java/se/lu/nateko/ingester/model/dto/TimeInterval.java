package se.lu.nateko.ingester.model.dto;

import java.time.Instant;

public class TimeInterval {
	private Instant start;
	private Instant stop;

	public TimeInterval(Instant start, Instant stop) {
		this.start = start;
		this.stop = stop;
	}

	public Instant getStart() {
		return start;
	}

	public Instant getStop() {
		return stop;
	}
}
