package com.wtsend.backend.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * What one user may see of another they have no relationship with.
 *
 * <p>
 * User search returned the full {@link UserResponse}, which carries email,
 * phone, birthday and the verification flags -- so any verified account could
 * harvest a full profile for any username it could guess. Only enough is
 * exposed here to recognise the person and send them a friend request.
 *
 * <p>
 * Deliberately not {@code FriendResponse}, despite the identical shape: a
 * search hit is by definition someone who is not a friend yet, and a type
 * named for the relationship would be wrong at every call site.
 */
@Getter
@Builder
public class UserSummaryResponse {
	private String id;
	private String username;
	private String displayName;
	private String avatarUrl;
}
