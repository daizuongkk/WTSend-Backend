package com.wtsend.backend.socket;

/**
 * Room names are prefixed by kind. Conversation ids and user ids previously
 * shared one namespace, so a client could join another user's personal room by
 * passing their id to "join-conversation".
 *
 * Server-side only -- clients still send a bare conversation id on the wire.
 */
public final class SocketRooms {
	private SocketRooms() {
	}

	public static String conversation(Long conversationId) {
		return "conversation:" + conversationId;
	}

	public static String user(String userId) {
		return "user:" + userId;
	}
}
