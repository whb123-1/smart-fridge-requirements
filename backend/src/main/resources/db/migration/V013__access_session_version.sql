ALTER TABLE app_user
  ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0 AFTER temporary_password_key_hash;
