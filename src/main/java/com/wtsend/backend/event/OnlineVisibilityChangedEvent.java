package com.wtsend.backend.event;

import java.util.UUID;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A user turned "show my online status" on or off.
 *
 * <p>
 * Published by the update that changed the column and handled after commit, so
 * nothing is announced to clients that the database has not accepted yet --
 * the same discipline as the message and conversation events.
 */
@Getter
@RequiredArgsConstructor
public class OnlineVisibilityChangedEvent {

  private final UUID userId;
  private final boolean visible;

}
