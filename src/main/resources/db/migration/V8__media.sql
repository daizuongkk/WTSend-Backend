CREATE TABLE media (
    id BINARY(16) NOT NULL,
    owner_id VARCHAR(255) NULL,
    original_name VARCHAR(255) NULL,
    content_type VARCHAR(100) NULL,
    size_bytes BIGINT NULL,
    storage_key VARCHAR(500) NULL,
    thumbnail_key VARCHAR(500) NULL,
    status VARCHAR(50) NULL,
    width INT NULL,
    height INT NULL,
    duration_sec INT NULL,
    fail_reason TEXT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
