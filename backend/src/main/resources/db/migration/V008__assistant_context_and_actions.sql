CREATE TABLE assistant_conversation (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  title VARCHAR(160) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_assistant_conversation_user (user_id, updated_at DESC),
  CONSTRAINT fk_assistant_conversation_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_context_snapshot (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  context_version CHAR(64) NOT NULL,
  context_json JSON NOT NULL,
  source_versions JSON NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_context_user_version (user_id, context_version),
  KEY ix_ai_context_expiry (expires_at),
  CONSTRAINT fk_ai_context_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE assistant_message (
  id BINARY(16) NOT NULL,
  conversation_id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  role VARCHAR(16) NOT NULL,
  content TEXT NOT NULL,
  page VARCHAR(64) NULL,
  selection_json JSON NULL,
  citations_json JSON NULL,
  context_snapshot_id BINARY(16) NULL,
  context_version CHAR(64) NULL,
  model_name VARCHAR(96) NULL,
  generation_status VARCHAR(24) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_assistant_message_conversation (conversation_id, created_at),
  KEY ix_assistant_message_user (user_id, created_at DESC),
  CONSTRAINT fk_assistant_message_conversation FOREIGN KEY (conversation_id) REFERENCES assistant_conversation(id),
  CONSTRAINT fk_assistant_message_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_assistant_message_snapshot FOREIGN KEY (context_snapshot_id) REFERENCES ai_context_snapshot(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE assistant_insight (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  insight_type VARCHAR(32) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_id BINARY(16) NOT NULL,
  context_version CHAR(64) NOT NULL,
  title VARCHAR(160) NOT NULL,
  body VARCHAR(1000) NOT NULL,
  citations_json JSON NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL,
  dismissed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_assistant_insight_dedup (user_id, insight_type, subject_id, context_version),
  KEY ix_assistant_insight_user (user_id, status, created_at DESC),
  CONSTRAINT fk_assistant_insight_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE assistant_action_proposal (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  conversation_id BINARY(16) NULL,
  action_type VARCHAR(48) NOT NULL,
  title VARCHAR(160) NOT NULL,
  payload_json JSON NOT NULL,
  context_version CHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_CONFIRMATION',
  result_json JSON NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) NULL,
  dismissed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY ix_assistant_proposal_user_status (user_id, status, created_at DESC),
  CONSTRAINT fk_assistant_proposal_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_assistant_proposal_conversation FOREIGN KEY (conversation_id) REFERENCES assistant_conversation(id),
  CONSTRAINT chk_assistant_action_type CHECK (action_type IN ('CREATE_SHOPPING_CANDIDATE','CREATE_RECIPE_CANDIDATES','CREATE_MEAL_DRAFT','CREATE_REMINDER_DRAFT','NAVIGATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
