-- Closes the race that lets two DIRECT conversations exist for the same pair.
--
-- The application looks a pair's thread up before creating one, but two
-- concurrent first messages both find nothing and both insert. The two users
-- then each read a different half of the history. Only the database can
-- actually arbitrate that, and a plain unique constraint cannot express
-- "these two participants" because the pair lives in two participants rows.
--
-- So DIRECT conversations carry a derived key: the two user ids ordered
-- lexicographically and joined, which makes (A,B) and (B,A) the same value.
-- GROUP conversations leave it NULL, and MySQL permits many NULLs in a unique
-- index, so the constraint applies only where it means something.

ALTER TABLE conversations
    ADD COLUMN direct_key VARCHAR(511) NULL AFTER type;

-- Backfill: ids are lowercase UUIDs, so MIN/MAX give the same ordering the
-- application's String comparison does. type = 0 is DIRECT (ORDINAL mapping).
UPDATE conversations c
JOIN (
    SELECT p.conversation_id,
           CONCAT(MIN(p.user_id), ':', MAX(p.user_id)) AS pair_key
    FROM participants p
    GROUP BY p.conversation_id
    HAVING COUNT(DISTINCT p.user_id) = 2
) pairs ON pairs.conversation_id = c.id
SET c.direct_key = pairs.pair_key
WHERE c.type = 0;

ALTER TABLE conversations
    ADD CONSTRAINT uk_conversations_direct_key UNIQUE (direct_key);
