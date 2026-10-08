package com.wtsend.backend.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * User, Friend and FriendRequest were all @Data, which generates toString,
 * equals and hashCode over every field -- including the bidirectional
 * associations between them. Logging a User with its friends loaded, or
 * putting one in a HashSet, recursed User -> Friend -> User until the stack
 * blew. Conversation, Message and Participant never had @Data and so were
 * never affected.
 */
class EntityRecursionTest {

	private static User userWithFriend() {
		User alice = new User();
		alice.setId("alice-id");
		User bob = new User();
		bob.setId("bob-id");

		Friend friendship = Friend.builder().id(1L).userA(alice).userB(bob).build();
		alice.setFriends1(new ArrayList<>(List.of(friendship)));
		bob.setFriends2(new ArrayList<>(List.of(friendship)));
		return alice;
	}

	private static User userWithFriendRequest() {
		User alice = new User();
		alice.setId("alice-id");
		User bob = new User();
		bob.setId("bob-id");

		FriendRequest request = FriendRequest.builder().id(1L).fromUser(alice).toUser(bob).build();
		alice.setSend(new ArrayList<>(List.of(request)));
		bob.setRecive(new ArrayList<>(List.of(request)));
		return alice;
	}

	@Test
	void aUserWithFriendsLoadedCanBeStringified() {
		User alice = userWithFriend();

		assertThatCode(alice::toString).doesNotThrowAnyException();
		assertThatCode(alice.getFriends1().get(0)::toString).doesNotThrowAnyException();
	}

	@Test
	void aUserWithFriendRequestsLoadedCanBeStringified() {
		User alice = userWithFriendRequest();

		assertThatCode(alice::toString).doesNotThrowAnyException();
		assertThatCode(alice.getSend().get(0)::toString).doesNotThrowAnyException();
	}

	@Test
	void aUserWithFriendsLoadedCanGoInAHashSet() {
		User alice = userWithFriend();

		assertThatCode(() -> new HashSet<>(List.of(alice))).doesNotThrowAnyException();
		assertThat(new HashSet<>(List.of(alice))).hasSize(1);
	}
}
