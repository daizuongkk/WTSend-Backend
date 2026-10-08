package com.wtsend.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The key is what the unique index in V2 constrains, so it has to be stable
 * under argument order -- otherwise the same pair yields two keys, the index
 * permits both, and the race it exists to stop is back.
 *
 * <p>
 * It also has to agree with the SQL backfill in V2, which orders with MIN/MAX.
 * User ids are lowercase UUIDs, so MySQL's case-insensitive collation and
 * Java's case-sensitive compareTo order them identically.
 */
class ConversationDirectKeyTest {

	private static final String A = "0fe5d97c-c30b-11f1-840f-6201e95acfe6";
	private static final String B = "7a2b1e40-aa31-4c0e-9f12-5d3c8b9e1177";

	@Test
	void argumentOrderDoesNotChangeTheKey() {
		assertThat(Conversation.directKeyOf(A, B)).isEqualTo(Conversation.directKeyOf(B, A));
	}

	@Test
	void theKeyIsTheTwoIdsInAscendingOrder() {
		assertThat(Conversation.directKeyOf(B, A)).isEqualTo(A + ":" + B);
	}

	@Test
	void differentPairsGetDifferentKeys() {
		String third = "ffffffff-ffff-4fff-8fff-ffffffffffff";

		assertThat(Conversation.directKeyOf(A, B))
				.isNotEqualTo(Conversation.directKeyOf(A, third));
	}
}
