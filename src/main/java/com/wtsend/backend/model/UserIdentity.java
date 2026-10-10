package com.wtsend.backend.model;

import java.time.Instant;

import com.wtsend.backend.model.enums.AuthProvider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One identity provider a user can sign in through.
 *
 * <p>
 * This replaced a single {@code authProvider} column on {@link User}, which
 * could only say how the account was first created -- so a user who signed up
 * with Google and later used the Facebook button was recorded as one or the
 * other and never both.
 *
 * <p>
 * {@code providerUserId} is the provider's own subject id and is what a login
 * looks up. {@code email} is what the provider reported at link time, kept for
 * support questions only: it is a snapshot, not a key, and the user's address
 * may since have diverged from it.
 *
 * <p>
 * LOCAL is not stored here. Whether password login works is already
 * {@code User.password != null}, so a LOCAL row would hold no subject and no
 * information.
 */
@Entity
@Table(name = "user_identity")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 20)
	private AuthProvider provider;

	/**
	 * Null only on rows backfilled by {@code V6__user_identity.sql}, where the
	 * subject was never recorded. The next login that matches the account by a
	 * provider-verified email fills it in.
	 */
	@Column(name = "provider_user_id")
	private String providerUserId;

	@Column(name = "email")
	private String email;

	@Column(name = "linked_at", nullable = false)
	private Instant linkedAt;
}
