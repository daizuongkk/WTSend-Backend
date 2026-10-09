package com.wtsend.backend.configs;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gives every request an id, in the MDC under {@code traceId}.
 *
 * <p>
 * Without this the trace id was decoration: the event envelope carried a
 * {@code traceId} field, the consumer put it in its MDC and the log pattern
 * printed it, but nothing ever populated it, so every record on the topic
 * carried {@code "traceId": null} and the whole chain printed an empty
 * placeholder.
 *
 * <p>
 * The point of it is the gap this phase introduced. An email is now sent on a
 * consumer thread, possibly minutes after -- and on a different instance from
 * -- the request that asked for it. The id is the only thing that lines the two
 * log lines up.
 *
 * <p>
 * An inbound {@code X-Request-Id} is honoured so a trace started by a proxy or
 * the frontend continues rather than being replaced, and it is echoed back so
 * whoever made the call can quote it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";
	public static final String MDC_KEY = "traceId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		String traceId = request.getHeader(HEADER);
		if (traceId == null || traceId.isBlank() || traceId.length() > 64)
			traceId = UUID.randomUUID().toString();

		MDC.put(MDC_KEY, traceId);
		response.setHeader(HEADER, traceId);
		try {
			chain.doFilter(request, response);
		} finally {
			// Threads are pooled, so leaving this behind would stamp the next
			// request on this thread with the previous one's id.
			MDC.remove(MDC_KEY);
		}
	}
}
