-- unread_counts was nullable with no default, and the increment is SQL
-- arithmetic: NULL + 1 is NULL. A row that ever held NULL was pinned there --
-- every later increment a no-op, and that conversation's unread badge dead for
-- good until something wrote a literal 0 over it.
--
-- Backfill first, then constrain, so this runs on a table that already has
-- rows: V1 was captured from a live database and permitted NULL, so such rows
-- plausibly exist.
UPDATE participants
SET unread_counts = 0
WHERE unread_counts IS NULL;

ALTER TABLE participants
    MODIFY unread_counts BIGINT NOT NULL DEFAULT 0;
