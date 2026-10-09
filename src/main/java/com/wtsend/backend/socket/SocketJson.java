package com.wtsend.backend.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The Jackson 2 settings every socket payload is written with.
 *
 * <p>
 * Jackson 2, not the Jackson 3 mapper the rest of the app uses: netty-socketio
 * serialises with Jackson 2 and that is not negotiable from here.
 *
 * <p>
 * It exists as one place because two mappers now write the same payloads --
 * netty-socketio's own, for a client attached to this instance, and
 * {@link SocketRelay}'s, for one attached to another. If only one of them
 * registered JavaTimeModule, the same message would reach two clients with
 * {@code createdAt} as an ISO string on one and an epoch array on the other,
 * and only in a multi-instance deployment.
 */
final class SocketJson {

	private SocketJson() {
	}

	static ObjectMapper configure(ObjectMapper mapper) {
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}

	static ObjectMapper mapper() {
		return configure(new ObjectMapper());
	}
}
