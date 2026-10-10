-- One row per identity provider a user can sign in through.
--
-- Login matches (provider, provider_user_id) -- the provider's own subject id --
-- and no longer matches on the email address. Email matching was an account
-- takeover path: anyone who could get a provider to report a victim's address
-- signed straight into the victim's account, and Facebook makes no promise that
-- the address Graph returns was ever verified.
--
-- provider_user_id is nullable only for the backfill below. Accounts created
-- before this table existed have no recorded subject, and the username
-- (google_<subject>, truncated to 20 characters) cannot reconstruct one. Those
-- rows heal themselves: the first login whose provider-verified email matches
-- the account writes the subject in. MySQL allows repeated NULLs in a UNIQUE
-- index, so the placeholders do not collide with each other.
CREATE TABLE user_identity (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          VARCHAR(255) NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NULL,
    email            VARCHAR(255) NULL,
    linked_at        DATETIME(6)  NOT NULL,
    CONSTRAINT pk_user_identity PRIMARY KEY (id),
    CONSTRAINT uk_user_identity_subject UNIQUE (provider, provider_user_id),
    CONSTRAINT uk_user_identity_user_provider UNIQUE (user_id, provider),
    -- ON DELETE CASCADE is load-bearing. UnverifiedAccountCleanupJob deletes
    -- through a bulk JPQL DELETE, which bypasses every JPA cascade, and a
    -- Facebook sign-up now starts unverified -- so without this the job would
    -- fail on a foreign key for exactly the accounts it exists to remove.
    CONSTRAINT fk_user_identity_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- LOCAL is deliberately not carried over: `password IS NOT NULL` already says
-- whether password login works, so a LOCAL row with no subject would record
-- nothing that is not already in the users table.
INSERT INTO user_identity (user_id, provider, provider_user_id, email, linked_at)
SELECT id, auth_provider, NULL, email, COALESCE(created_at, NOW(6))
  FROM users
 WHERE auth_provider IN ('GOOGLE', 'FACEBOOK');

ALTER TABLE users DROP COLUMN auth_provider;
