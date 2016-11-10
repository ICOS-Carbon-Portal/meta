package se.lu.nateko.cp.meta;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class HttpApiEtagWarningFilter extends Filter<ILoggingEvent> {

	@Override
	public FilterReply decide(ILoggingEvent event) {
		if (event.getFormattedMessage().startsWith("Illegal response header: Illegal 'etag' header: Invalid input"))
			return FilterReply.DENY;
		return FilterReply.ACCEPT;
	}
}
