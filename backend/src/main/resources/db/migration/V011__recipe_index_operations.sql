ALTER TABLE recipe_import_job
  ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER error_json,
  ADD COLUMN last_error VARCHAR(1000) NULL AFTER attempt_count;

CREATE TABLE recipe_search_index_config (
  singleton_id TINYINT NOT NULL,
  active_collection VARCHAR(160) NULL,
  embedding_model_version VARCHAR(96) NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (singleton_id),
  CONSTRAINT chk_recipe_index_singleton CHECK (singleton_id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO recipe_search_index_config
  (singleton_id, active_collection, embedding_model_version, updated_at)
VALUES (1, NULL, NULL, UTC_TIMESTAMP(3));

CREATE TABLE recipe_index_rebuild_job (
  id BINARY(16) NOT NULL,
  requested_by BINARY(16) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
  collection_name VARCHAR(160) NOT NULL,
  embedding_model_version VARCHAR(96) NOT NULL,
  total_count INT NOT NULL DEFAULT 0,
  processed_count INT NOT NULL DEFAULT 0,
  failure_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL,
  started_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL,
  locked_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recipe_rebuild_collection (collection_name),
  KEY ix_recipe_rebuild_claim (status, locked_at, created_at),
  CONSTRAINT fk_recipe_rebuild_user FOREIGN KEY (requested_by) REFERENCES app_user(id),
  CONSTRAINT chk_recipe_rebuild_status CHECK (status IN ('QUEUED','PROCESSING','COMPLETED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
