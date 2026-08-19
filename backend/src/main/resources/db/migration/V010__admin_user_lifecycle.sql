ALTER TABLE app_user
  MODIFY COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER',
  ADD COLUMN last_login_at DATETIME(3) NULL AFTER onboarding_completed_at,
  ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE AFTER last_login_at,
  ADD COLUMN temporary_password_expires_at DATETIME(3) NULL AFTER password_change_required,
  ADD COLUMN temporary_password_key_hash CHAR(64) NULL AFTER temporary_password_expires_at,
  ADD COLUMN deletion_requested_at DATETIME(3) NULL AFTER temporary_password_key_hash,
  ADD COLUMN anonymized_at DATETIME(3) NULL AFTER deletion_requested_at,
  ADD CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
  ADD CONSTRAINT chk_app_user_role CHECK (role IN ('USER', 'ADMIN')),
  ADD KEY ix_app_user_admin_search (role, status, deleted_at, created_at),
  ADD KEY ix_app_user_anonymization (deleted_at, anonymized_at);

ALTER TABLE audit_log
  ADD COLUMN target_user_id BINARY(16) NULL AFTER user_id,
  ADD KEY ix_audit_target_created (target_user_id, created_at),
  ADD CONSTRAINT fk_audit_target_user FOREIGN KEY (target_user_id) REFERENCES app_user(id);

CREATE TABLE identity_tombstone (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  username_hmac CHAR(64) NOT NULL,
  email_hmac CHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_identity_tombstone_user (user_id),
  UNIQUE KEY uk_identity_tombstone_username (username_hmac),
  UNIQUE KEY uk_identity_tombstone_email (email_hmac),
  CONSTRAINT fk_identity_tombstone_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
