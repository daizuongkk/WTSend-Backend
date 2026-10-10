package com.wtsend.backend.model.enums;

/**
 * The identity providers a {@link com.wtsend.backend.model.UserIdentity} can
 * record.
 *
 * <p>
 * No LOCAL. Password login is {@code User.password != null}, so a LOCAL
 * identity row would carry no subject id and say nothing the users table does
 * not already say -- and it would be the one value of this enum that could
 * never appear in the column this enum types.
 */
public enum AuthProvider {
	GOOGLE, FACEBOOK
}
