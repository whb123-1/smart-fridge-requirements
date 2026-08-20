ALTER TABLE inventory_transaction
  ADD COLUMN deleted_at DATETIME(3) NULL AFTER created_at,
  ADD KEY ix_inventory_transaction_deleted (actor_user_id, deleted_at, created_at DESC);

ALTER TABLE recipe
  ADD COLUMN nutrition_source VARCHAR(64) NOT NULL DEFAULT 'AI_CATALOG_SEARCH' AFTER carbs_total;

UPDATE recipe SET nutrition_source='AI_CATALOG_SEARCH';

CREATE TABLE recipe_synthesis_session (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  recipe_id BINARY(16) NULL,
  input_json JSON NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  executed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY ix_recipe_synthesis_user_time (user_id, created_at DESC),
  CONSTRAINT fk_recipe_synthesis_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_recipe_synthesis_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id),
  CONSTRAINT chk_recipe_synthesis_status CHECK (status IN ('MATCHED','UNMATCHED','EXECUTED','ABANDONED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
