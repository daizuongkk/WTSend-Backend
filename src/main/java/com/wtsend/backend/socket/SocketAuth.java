package com.wtsend.backend.socket;

import java.time.Instant;

/**
 * What the handshake established about a socket: who it is, and until when.
 *
 * <p>
 * Stashed on the {@code HandshakeData} by {@code SocketIOConfig} so that
 * {@code onConnect} does not have to decode and verify the token a second time.
 * It used to be the bare user id, which is why nothing could tell when the
 * credential behind a connection expired.
 *
 * <p>
 * {@code expiresAt} is nullable only because a {@code Jwt} may be: the decoder's
 * default validator rejects a missing {@code exp}, so in practice it is always
 * present, and a null simply means the socket is not expired on a timer.
 */
public record SocketAuth(String userId, Instant expiresAt) {
}
