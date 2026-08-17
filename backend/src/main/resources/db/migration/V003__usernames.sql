ALTER TABLE app_user
  ADD COLUMN username VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER email;

CREATE TEMPORARY TABLE username_backfill (
  user_id BINARY(16) NOT NULL,
  candidate VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (user_id)
);

INSERT INTO username_backfill (user_id, candidate)
SELECT user.id, LOWER(TRIM(user.display_name))
FROM app_user user
JOIN (
  SELECT LOWER(TRIM(display_name)) AS candidate
  FROM app_user
  WHERE REGEXP_LIKE(LOWER(TRIM(display_name)), '^[a-z0-9_]{3,32}$', 'c')
  GROUP BY LOWER(TRIM(display_name))
  HAVING COUNT(*) = 1
) available ON available.candidate = LOWER(TRIM(user.display_name))
WHERE NOT EXISTS (
  SELECT 1 FROM app_user reserved
  WHERE LOWER(HEX(reserved.id)) = available.candidate
);

UPDATE app_user user
JOIN username_backfill backfill ON backfill.user_id = user.id
SET user.username = backfill.candidate;

UPDATE app_user
SET username = LOWER(HEX(id))
WHERE username IS NULL;

DROP TEMPORARY TABLE username_backfill;

ALTER TABLE app_user
  MODIFY COLUMN username VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  ADD UNIQUE KEY uk_app_user_username (username),
  ADD CONSTRAINT chk_app_user_username
    CHECK (REGEXP_LIKE(username, '^[a-z0-9_]{3,32}$', 'c'));
