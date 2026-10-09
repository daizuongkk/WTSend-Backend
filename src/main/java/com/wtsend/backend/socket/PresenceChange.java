package com.wtsend.backend.socket;

/**
 * One user's presence changed. Broadcast instead of the full online-user list,
 * which was previously resent to every connected socket on every connect and
 * disconnect -- O(users squared) traffic for a single person opening a tab.
 */
public record PresenceChange(String userId, boolean online) {
}
