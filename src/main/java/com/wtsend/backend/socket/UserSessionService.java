package com.wtsend.backend.socket;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.corundumstudio.socketio.SocketIOClient;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.services.ConversationService;
import com.wtsend.backend.services.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSessionService {
  private final UserService userService;
  private final RedisPresenceStore onlineUsers;
  private final ConversationService conversationService;
  private final PresenceAnnouncer announcer;

  public void onClientConnected(Object authToken, SocketIOClient client) {
    if (authToken == null) {
      client.disconnect();
      return;
    }
    String userId = authToken.toString();
    UserResponse user;
    try {
      // findById throws for an unknown id, it never returns null -- unguarded,
      // a deleted user threw straight out of netty's connect listener. Catching
      // AppException rather than RuntimeException so a genuine mapping failure
      // still surfaces instead of being reported as "no such user".
      user = userService.findById(UUID.fromString(userId));
    } catch (AppException e) {
      log.warn("Rejecting socket {}: no user for id {}", client.getSessionId(), userId);
      client.disconnect();
      return;
    }
    client.set("user", user);

    // Before anything is announced. Presence is addressed to personal rooms, so
    // a socket that has not joined its own yet cannot receive the arrival of
    // anyone who connects in the window between the snapshot below and the
    // join -- and the snapshot would already have been taken. The conversation
    // rooms still happen in subscribeUserToConversations, after a query.
    client.joinRoom(SocketRooms.user(user.getId()));

    boolean cameOnline = onlineUsers.add(user.getId(), client.getSessionId(),
        user.isShowOnlineStatus());
    log.info("{} connected with socket {}", user.getUsername(), client.getSessionId());

    // The snapshot goes to the arriving client alone. It used to be broadcast
    // to everyone on every connect, so one person opening a tab resent the
    // whole online list to every socket.
    // Only the people entitled to see it, not the whole online list. It used
    // to be every online user id, so a new account learned who else was using
    // the app before it had a single friend.
    // Queried, not cached on the socket. Caching it meant a friendship made
    // during the session was invisible to both sides until one of them
    // reconnected -- they showed each other offline in a conversation they
    // were visibly typing in. One two-EXISTS query per connect or disconnect
    // is a price worth paying to not have a second source of truth.
    List<UUID> audience = announcer.audienceFor(user.getId());

    Set<UUID> visible = new LinkedHashSet<>(audience);
    visible.retainAll(onlineUsers.onlineUserIds());
    client.sendEvent("online-users", visible);

    // A user who has turned their status off is not announced, and was never
    // put in the online list to begin with.
    if (cameOnline && user.isShowOnlineStatus())
      announcer.announce(user.getId(), audience, true);
  }

  /**
   * A socket is NOT closed when the access token that opened it expires, and
   * that is deliberate.
   *
   * <p>
   * It was, briefly. The access token lives five minutes, so every socket in
   * the system was torn down and rebuilt every five minutes -- and each
   * teardown is a real disconnect, which drops the user from the online list
   * and announces them offline to every friend. Everyone blinked offline on a
   * timer, and every realtime frame in the gap was lost, because the server
   * does not replay. The protection it bought was thin by comparison: the
   * socket carries no authority of its own, every write still goes through the
   * REST API, and that API does check the token on every request.
   */
  public void onClientDisconnected(SocketIOClient client) {
    UserResponse user = client.get("user");
    if (user == null)
      return;

    // Only the last socket closing takes the user offline; a second tab must
    // not look like a disconnect.
    // Not gated on showOnlineStatus, deliberately. The UserResponse cached on
    // this socket is from connect time, so it can be stale if the user has
    // toggled since -- and telling clients that an already-invisible user is
    // offline changes nothing for them, while skipping it wrongly would leave
    // someone shown online after they left.
    if (onlineUsers.remove(user.getId(), client.getSessionId()))
      announcer.announce(user.getId(), false);

    log.info("{} disconnected.", user.getUsername());
  }

  public void subscribeUserToConversations(SocketIOClient client) {
    UserResponse user = client.get("user");
    if (user == null)
      return;

    // The personal room is joined in onClientConnected, before any presence is
    // announced. It used to be joined here, after the loop below and behind an
    // early return for "this user has no conversations yet" -- so a brand new
    // account joined no room at all, and since every message is addressed to
    // the participants' personal rooms, nothing could ever be delivered to it.
    List<Long> conversationIds = conversationService.getConversationIdsByUserId(user.getId());
    if (conversationIds == null || conversationIds.isEmpty())
      return;

    conversationIds.forEach(ci -> {
      client.joinRoom(SocketRooms.conversation(ci));
      log.info("{} joined to room {} ", user.getUsername(), ci);
    });
  }

}
