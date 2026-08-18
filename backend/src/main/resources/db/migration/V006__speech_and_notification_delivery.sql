CREATE TABLE voice_ingestion (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  fridge_id BINARY(16) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'UPLOADED',
  object_key VARCHAR(512) NOT NULL,
  original_filename VARCHAR(255) NULL,
  content_type VARCHAR(96) NOT NULL,
  content_length BIGINT NOT NULL,
  transcript_text TEXT NULL,
  draft_json JSON NULL,
  failure_code VARCHAR(64) NULL,
  failure_reason VARCHAR(1000) NULL,
  expires_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY ix_voice_ingestion_user_status (user_id, status, created_at DESC),
  KEY ix_voice_ingestion_expiry (status, expires_at),
  CONSTRAINT fk_voice_ingestion_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_voice_ingestion_fridge FOREIGN KEY (fridge_id) REFERENCES fridge(id),
  CONSTRAINT chk_voice_ingestion_status CHECK (
    status IN ('UPLOADED', 'TRANSCRIBING', 'READY', 'FAILED', 'CONFIRMED', 'EXPIRED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_preference (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  notification_type VARCHAR(32) NOT NULL,
  in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  quiet_start TIME NULL,
  quiet_end TIME NULL,
  timezone VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_preference_user_type (user_id, notification_type),
  KEY ix_notification_preference_user (user_id, updated_at),
  CONSTRAINT fk_notification_preference_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT chk_notification_preference_type CHECK (
    notification_type IN ('EXPIRY_SOON', 'EXPIRED', 'LOW_STOCK', 'ENVIRONMENT_ALERT')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE notification
  ADD COLUMN in_app_visible BOOLEAN NOT NULL DEFAULT TRUE AFTER deep_link;

CREATE TABLE notification_delivery (
  id BINARY(16) NOT NULL,
  notification_id BINARY(16) NOT NULL,
  channel VARCHAR(16) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  available_at DATETIME(3) NOT NULL,
  last_attempt_at DATETIME(3) NULL,
  delivered_at DATETIME(3) NULL,
  failure_reason VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_delivery_channel (notification_id, channel),
  KEY ix_notification_delivery_due (status, available_at),
  CONSTRAINT fk_notification_delivery_notification FOREIGN KEY (notification_id) REFERENCES notification(id),
  CONSTRAINT chk_notification_delivery_channel CHECK (channel IN ('IN_APP', 'EMAIL')),
  CONSTRAINT chk_notification_delivery_status CHECK (
    status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'SKIPPED', 'FAILED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
