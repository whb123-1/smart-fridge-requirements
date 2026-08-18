CREATE TABLE recipe_version (
  id BINARY(16) NOT NULL,
  recipe_id BINARY(16) NOT NULL,
  version_number INT NOT NULL,
  snapshot_json JSON NOT NULL,
  checksum CHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recipe_version_number (recipe_id, version_number),
  UNIQUE KEY uk_recipe_version_checksum (recipe_id, checksum),
  CONSTRAINT fk_recipe_version_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE recipe_import_job
  ADD COLUMN started_at DATETIME(3) NULL AFTER created_at,
  ADD COLUMN locked_at DATETIME(3) NULL AFTER started_at;

CREATE INDEX ix_recipe_import_claim
  ON recipe_import_job (status, locked_at, created_at);
