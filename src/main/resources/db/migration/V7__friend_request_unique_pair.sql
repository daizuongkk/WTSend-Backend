-- One pending request per direction.
--
-- sendFriendRequest checks for an existing request and then inserts, with
-- nothing holding the two together, so two clicks landing together produced two
-- rows for the same pair -- and the recipient saw the same request twice, could
-- accept one and still be looking at the other.
--
-- The duplicates have to go before the index can exist. MIN(id) is the one that
-- was really acted on; the rest are the losers of that race.
DELETE later
  FROM friend_request AS later
  JOIN (
        SELECT from_user, to_user, MIN(id) AS keep
          FROM friend_request
         WHERE from_user IS NOT NULL AND to_user IS NOT NULL
         GROUP BY from_user, to_user
        HAVING COUNT(*) > 1
       ) AS dup
    ON later.from_user = dup.from_user
   AND later.to_user = dup.to_user
   AND later.id > dup.keep;

ALTER TABLE friend_request
    ADD CONSTRAINT uk_friend_request_pair UNIQUE (from_user, to_user);
