package com.wtsend.backend.socket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Presence is broadcast as a delta now, so the store has to distinguish "a
 * socket changed" from "this user's presence changed". Without that, opening a
 * second tab announces the user as newly online, and closing it announces them
 * offline while they are still connected elsewhere.
 */
class OnlineUserStoreTest {

	private static final String ALICE = "alice-id";

	private final OnlineUserStore store = new OnlineUserStore();

	@Test
	void theFirstSocketBringsTheUserOnline() {
		assertThat(store.add(ALICE, UUID.randomUUID())).isTrue();
	}

	@Test
	void aSecondSocketIsNotANewArrival() {
		store.add(ALICE, UUID.randomUUID());

		assertThat(store.add(ALICE, UUID.randomUUID())).isFalse();
	}

	@Test
	void closingOneOfTwoSocketsDoesNotTakeTheUserOffline() {
		UUID firstTab = UUID.randomUUID();
		UUID secondTab = UUID.randomUUID();
		store.add(ALICE, firstTab);
		store.add(ALICE, secondTab);

		assertThat(store.remove(ALICE, firstTab)).isFalse();
		assertThat(store.onlineUserIds()).contains(ALICE);
	}

	@Test
	void theLastSocketTakesTheUserOffline() {
		UUID onlyTab = UUID.randomUUID();
		store.add(ALICE, onlyTab);

		assertThat(store.remove(ALICE, onlyTab)).isTrue();
		assertThat(store.onlineUserIds()).doesNotContain(ALICE);
	}

	@Test
	void removingAnUnknownSocketChangesNothing() {
		assertThat(store.remove(ALICE, UUID.randomUUID())).isFalse();
	}

	@Test
	void reconnectingAfterGoingOfflineCountsAsANewArrival() {
		UUID firstSession = UUID.randomUUID();
		store.add(ALICE, firstSession);
		store.remove(ALICE, firstSession);

		assertThat(store.add(ALICE, UUID.randomUUID())).isTrue();
	}
}
