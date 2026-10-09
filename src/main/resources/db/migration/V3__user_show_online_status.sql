-- Whether other people may see this user as online.
--
-- Defaults to 1 so every existing row keeps the behaviour it had, and NOT NULL
-- because "unknown" has no sensible reading for a privacy switch -- a NULL
-- would have to be guessed at by every read, and guessing "visible" is the
-- wrong way to be wrong.
ALTER TABLE users
    ADD COLUMN show_online_status BIT(1) NOT NULL DEFAULT 1;
