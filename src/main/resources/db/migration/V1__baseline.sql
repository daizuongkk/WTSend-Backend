-- Baseline of the schema Hibernate's ddl-auto=update had been generating.
--
-- Captured from a live database rather than hand-derived, then verified by
-- dropping the schema and letting Flyway rebuild it with ddl-auto=validate --
-- so this file and the JPA entities are known to agree.
--
-- Constraint names are readable here instead of Hibernate's generated hashes
-- (FKd9sy3cjuppb511olt5pv3ixe and friends). Hibernate's validate only checks
-- tables, columns and types, never constraint or index names.

CREATE TABLE users (
    id             VARCHAR(255) NOT NULL,
    username       VARCHAR(255) NULL,
    email          VARCHAR(255) NOT NULL,
    password       VARCHAR(255) NULL,
    display_name   VARCHAR(255) NOT NULL,
    phone          VARCHAR(255) NULL,
    birthday       DATE         NULL,
    bio            VARCHAR(500) NULL,
    avatar_url     VARCHAR(255) NULL,
    avatar_id      VARCHAR(255) NULL,
    -- Mapped as a native MySQL ENUM by Hibernate. Never written by any code
    -- path today, so every row is NULL; scheduled for removal.
    auth_provider  ENUM('FACEBOOK','GOOGLE','LOCAL') NULL,
    email_verified BIT(1)       NULL,
    phone_verified BIT(1)       NULL,
    created_at     DATETIME(6)  NULL,
    updated_at     DATETIME(6)  NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE = InnoDB;

-- conversations.last_message_id and messages.conversation_id reference each
-- other, so that one FK is added after both tables exist.
CREATE TABLE conversations (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    -- ConversationType is mapped ORDINAL: 0 = DIRECT, 1 = GROUP. Reordering
    -- the enum silently remaps existing rows.
    type            TINYINT     NULL,
    last_message_id BIGINT      NULL,
    last_message_at DATETIME(6) NULL,
    created_at      DATETIME(6) NULL,
    updated_at      DATETIME(6) NULL,
    CONSTRAINT pk_conversations PRIMARY KEY (id),
    CONSTRAINT uk_conversations_last_message UNIQUE (last_message_id),
    CONSTRAINT ck_conversations_type CHECK (type BETWEEN 0 AND 1)
) ENGINE = InnoDB;

CREATE TABLE messages (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NULL,
    sender_id       VARCHAR(255) NULL,
    content         TEXT         NULL,
    img_url         VARCHAR(255) NULL,
    -- DATETIME(6) is load-bearing: message cursors are a keyset on
    -- (created_at, id) and losing the sub-second part would make every message
    -- in the same second tie. See com.wtsend.backend.libs.MessageCursor.
    created_at      DATETIME(6)  NULL,
    updated_at      DATETIME(6)  NULL,
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id)
) ENGINE = InnoDB;

ALTER TABLE conversations
    ADD CONSTRAINT fk_conversations_last_message FOREIGN KEY (last_message_id) REFERENCES messages (id);

CREATE TABLE participants (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id      BIGINT       NULL,
    user_id              VARCHAR(255) NULL,
    last_seen_message_id BIGINT       NULL,
    last_read_at         DATETIME(6)  NULL,
    unread_counts        BIGINT       NULL,
    joined_at            DATETIME(6)  NULL,
    created_at           DATETIME(6)  NULL,
    updated_at           DATETIME(6)  NULL,
    CONSTRAINT pk_participants PRIMARY KEY (id),
    CONSTRAINT uk_participants_conversation_user UNIQUE (conversation_id, user_id),
    CONSTRAINT fk_participants_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_participants_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_participants_last_seen_message FOREIGN KEY (last_seen_message_id) REFERENCES messages (id)
) ENGINE = InnoDB;

-- Shares its primary key with conversations (@OneToOne @MapsId).
CREATE TABLE group_info (
    conversationid BIGINT       NOT NULL,
    name           VARCHAR(255) NULL,
    avatar_url     VARCHAR(255) NULL,
    avatar_id      VARCHAR(255) NULL,
    creator_id     VARCHAR(255) NULL,
    CONSTRAINT pk_group_info PRIMARY KEY (conversationid),
    CONSTRAINT fk_group_info_conversation FOREIGN KEY (conversationid) REFERENCES conversations (id),
    CONSTRAINT fk_group_info_creator FOREIGN KEY (creator_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE friend (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    usera      VARCHAR(255) NULL,
    userb      VARCHAR(255) NULL,
    created_at DATETIME(6)  NULL,
    updated_at DATETIME(6)  NULL,
    CONSTRAINT pk_friend PRIMARY KEY (id),
    CONSTRAINT uk_friend_pair UNIQUE (usera, userb),
    CONSTRAINT fk_friend_usera FOREIGN KEY (usera) REFERENCES users (id),
    CONSTRAINT fk_friend_userb FOREIGN KEY (userb) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE friend_request (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    from_user  VARCHAR(255) NULL,
    to_user    VARCHAR(255) NULL,
    message    VARCHAR(255) NULL,
    created_at DATETIME(6)  NULL,
    updated_at DATETIME(6)  NULL,
    CONSTRAINT pk_friend_request PRIMARY KEY (id),
    CONSTRAINT fk_friend_request_from FOREIGN KEY (from_user) REFERENCES users (id),
    CONSTRAINT fk_friend_request_to FOREIGN KEY (to_user) REFERENCES users (id)
) ENGINE = InnoDB;
