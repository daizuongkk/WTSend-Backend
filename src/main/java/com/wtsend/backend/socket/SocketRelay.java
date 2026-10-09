package com.wtsend.backend.socket;

import java.util.Collection;
import java.util.List;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtsend.backend.dto.response.ConversationResponse;
import com.wtsend.backend.dto.response.ParticipantResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends a socket event to every client in a room, on every instance.
 *
 * <p>
 * A netty-socketio server only knows the clients connected to itself, so with
 * two instances behind a load balancer a message sent by someone on A never
 * reached the half of the conversation sitting on B. Each emission is therefore
 * done locally and also published on a Redis channel; every instance emits what
 * it receives to its own clients.
 *
 * <p>
 * Room membership is deliberately not shared. Every client joins its rooms on
 * the instance it connected to, so each instance already has the right
 * membership for the clients it holds -- which is why this does not need
 * netty-socketio's Redisson store and the dependency that comes with it. The
 * limit of that choice: {@code getRoomOperations(...)} and
 * {@code getAllClients()} stay node-local, so counting or listing clients
 * across the cluster is not something this supports.
 *
 * <p>
 * The envelope carries the payload as a {@link JsonNode} rather than a typed
 * object, so no Java class name travels on the wire. A channel subscriber that
 * instantiated classes named in the message would be a deserialisation gadget
 * aimed at whoever can write to Redis.
 */
@Component
@Slf4j
public class SocketRelay implements MessageListener {

	static final String CHANNEL = "wtsend:socket:relay";

	/** Written by every instance, so it has to agree byte for byte. */
	private final ObjectMapper json = SocketJson.mapper();

	private final SocketIOServer server;
	private final StringRedisTemplate redis;
	private final InstanceId instanceId;

	public SocketRelay(SocketIOServer server, StringRedisTemplate redis, InstanceId instanceId) {
		this.server = server;
		this.redis = redis;
		this.instanceId = instanceId;
	}

	/** Emits to one room here and on every other instance. */
	public void toRoom(String room, String event, Object payload) {
		toRooms(List.of(room), event, payload);
	}

	/**
	 * Emits to every member of a conversation, addressed by their personal
	 * rooms.
	 *
	 * <p>
	 * Not the conversation's own room, and that is the point. A personal room is
	 * joined once at connect and never changes, while the conversation room has
	 * to be joined -- so a conversation created after a client connected had
	 * nobody in its room, and every message in it was delivered to no one until
	 * that client reconnected. The first message to a new friend, the one that
	 * creates the conversation, could never arrive in real time at all.
	 */
	public void toConversation(ConversationResponse conversation, String event, Object payload) {
		toRooms(conversation.getParticipants().stream()
				.map(ParticipantResponse::getUserId)
				.map(SocketRooms::user)
				.toList(), event, payload);
	}

	/** Emits to several rooms here and on every other instance. */
	public void toRooms(Collection<String> rooms, String event, Object payload) {
		rooms.forEach(room -> server.getRoomOperations(room).sendEvent(event, payload));
		publish(rooms, event, payload);
	}

	/** Emits to every connected client, here and on every other instance. */
	public void toAll(String event, Object payload) {
		server.getBroadcastOperations().sendEvent(event, payload);
		publish(null, event, payload);
	}

	private void publish(Collection<String> rooms, String event, Object payload) {
		try {
			// One envelope whatever the room count: a 50-member group would
			// otherwise be 50 round trips to Redis for a single message.
			Envelope envelope = new Envelope(instanceId.value(),
					rooms == null ? null : List.copyOf(rooms), event, json.valueToTree(payload));
			redis.convertAndSend(CHANNEL, json.writeValueAsString(envelope));
		} catch (Exception e) {
			// The local emission already happened, so clients on this instance are
			// served either way. Rethrowing would only propagate into an
			// AFTER_COMMIT listener, where it is swallowed.
			log.error("Failed to relay {} to {}", event, rooms, e);
		}
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			Envelope envelope = json.readValue(message.getBody(), Envelope.class);

			// Our own publication comes back to us as a subscriber; emitting it
			// again would deliver every message twice to the sender's own clients.
			if (instanceId.value().equals(envelope.from()))
				return;

			if (envelope.rooms() == null)
				server.getBroadcastOperations().sendEvent(envelope.event(), envelope.payload());
			else
				envelope.rooms().forEach(room -> server.getRoomOperations(room)
						.sendEvent(envelope.event(), envelope.payload()));
		} catch (Exception e) {
			log.error("Failed to apply a relayed socket event", e);
		}
	}

	/** {@code rooms == null} means every client rather than a set of rooms. */
	record Envelope(String from, List<String> rooms, String event, JsonNode payload) {
	}
}
