ALTER TABLE idempotency_record
  ADD COLUMN http_method VARCHAR(10) NULL,
  ADD COLUMN request_path VARCHAR(255) NULL,
  ADD COLUMN status_code INT NOT NULL DEFAULT 200;

CREATE TABLE food_catalog (
  id BINARY(16) NOT NULL,
  canonical_name VARCHAR(120) NOT NULL,
  aliases VARCHAR(512) NULL,
  category VARCHAR(24) NOT NULL,
  default_unit VARCHAR(24) NOT NULL,
  default_shelf_life_days INT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_food_catalog_name (canonical_name),
  KEY ix_food_catalog_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE food_weight_estimate (
  id BINARY(16) NOT NULL,
  catalog_id BINARY(16) NOT NULL,
  label VARCHAR(80) NOT NULL,
  reference_grams DECIMAL(12,3) NOT NULL,
  unit VARCHAR(24) NOT NULL,
  source VARCHAR(120) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_food_weight_catalog (catalog_id),
  CONSTRAINT fk_food_weight_catalog FOREIGN KEY (catalog_id) REFERENCES food_catalog(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE food_storage_profile (
  id BINARY(16) NOT NULL,
  category VARCHAR(24) NOT NULL,
  zone_kind VARCHAR(24) NULL,
  profile_version INT NOT NULL DEFAULT 1,
  unopened_hours INT NULL,
  opened_hours INT NULL,
  risk_coefficient DECIMAL(8,3) NOT NULL DEFAULT 1.000,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_food_storage_profile (category, zone_kind, profile_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_item (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  fridge_id BINARY(16) NOT NULL,
  catalog_id BINARY(16) NULL,
  display_name VARCHAR(120) NOT NULL,
  category VARCHAR(24) NOT NULL,
  low_stock_quantity DECIMAL(14,3) NULL,
  default_unit VARCHAR(24) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  deleted_at DATETIME(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY ix_inventory_item_user (user_id, fridge_id, deleted_at),
  KEY ix_inventory_item_category (user_id, category, deleted_at),
  CONSTRAINT fk_inventory_item_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_inventory_item_fridge FOREIGN KEY (fridge_id) REFERENCES fridge(id),
  CONSTRAINT fk_inventory_item_catalog FOREIGN KEY (catalog_id) REFERENCES food_catalog(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_batch (
  id BINARY(16) NOT NULL,
  item_id BINARY(16) NOT NULL,
  zone_id BINARY(16) NULL,
  stored_at DATETIME(3) NOT NULL,
  opened_at DATETIME(3) NULL,
  package_expires_at DATETIME(3) NULL,
  shelf_life_days INT NULL,
  initial_quantity DECIMAL(14,3) NOT NULL,
  remaining_quantity DECIMAL(14,3) NOT NULL,
  unit VARCHAR(24) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  remind_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY ix_inventory_batch_item_status (item_id, status),
  KEY ix_inventory_batch_zone_status (zone_id, status),
  CONSTRAINT fk_inventory_batch_item FOREIGN KEY (item_id) REFERENCES inventory_item(id),
  CONSTRAINT fk_inventory_batch_zone FOREIGN KEY (zone_id) REFERENCES fridge_zone(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_transaction (
  id BINARY(16) NOT NULL,
  batch_id BINARY(16) NOT NULL,
  type VARCHAR(24) NOT NULL,
  before_quantity DECIMAL(14,3) NOT NULL,
  after_quantity DECIMAL(14,3) NOT NULL,
  quantity_delta DECIMAL(14,3) NOT NULL,
  unit VARCHAR(24) NOT NULL,
  source_type VARCHAR(40) NULL,
  source_id BINARY(16) NULL,
  actor_user_id BINARY(16) NOT NULL,
  idempotency_key VARCHAR(128) NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_inventory_transaction_batch (batch_id, created_at),
  CONSTRAINT fk_inventory_transaction_batch FOREIGN KEY (batch_id) REFERENCES inventory_batch(id),
  CONSTRAINT fk_inventory_transaction_user FOREIGN KEY (actor_user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shelf_life_assessment (
  id BINARY(16) NOT NULL,
  batch_id BINARY(16) NOT NULL,
  profile_version INT NULL,
  estimated_expiry_at DATETIME(3) NULL,
  base_expiry_at DATETIME(3) NULL,
  cumulative_risk_minutes DECIMAL(14,3) NOT NULL DEFAULT 0,
  estimation_source VARCHAR(32) NOT NULL,
  confidence VARCHAR(24) NOT NULL,
  safety_status VARCHAR(32) NOT NULL,
  explanation VARCHAR(512) NOT NULL,
  calculated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_shelf_assessment_batch_time (batch_id, calculated_at DESC),
  CONSTRAINT fk_shelf_assessment_batch FOREIGN KEY (batch_id) REFERENCES inventory_batch(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shopping_list (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  fridge_id BINARY(16) NOT NULL,
  name VARCHAR(120) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_shopping_list_user (user_id, fridge_id),
  CONSTRAINT fk_shopping_list_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_shopping_list_fridge FOREIGN KEY (fridge_id) REFERENCES fridge(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shopping_item (
  id BINARY(16) NOT NULL,
  list_id BINARY(16) NOT NULL,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(24) NOT NULL,
  quantity DECIMAL(14,3) NULL,
  unit VARCHAR(24) NULL,
  note VARCHAR(255) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY ix_shopping_item_list_status (list_id, status),
  CONSTRAINT fk_shopping_item_list FOREIGN KEY (list_id) REFERENCES shopping_list(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE outbox_event (
  id BINARY(16) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id BINARY(16) NOT NULL,
  event_type VARCHAR(96) NOT NULL,
  payload_json JSON NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  available_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_outbox_status_available (status, available_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO food_catalog (id, canonical_name, aliases, category, default_unit, default_shelf_life_days, created_at, updated_at) VALUES
(UUID_TO_BIN(UUID()), '上海青', '小青菜,青菜', 'VEGETABLE', 'g', 5, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), '鲜牛奶', '牛奶,纯牛奶', 'DAIRY', 'ml', 7, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), '鸡胸肉', '鸡胸,鸡脯肉', 'MEAT_EGG', 'g', 3, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), '北豆腐', '豆腐', 'BEAN', 'box', 5, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), '鸡蛋', '蛋,鸡子', 'MEAT_EGG', 'piece', 21, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), '三文鱼', '鲑鱼', 'SEAFOOD', 'g', 2, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), '无糖酸奶', '酸奶', 'DAIRY', 'cup', 14, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), '低钠生抽', '生抽,酱油', 'CONDIMENT', 'ml', 180, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), '燕麦片', '燕麦', 'OTHER', 'g', 180, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), '小番茄', '圣女果,番茄', 'FRUIT', 'g', 7, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), '虾仁', '虾', 'SEAFOOD', 'g', 2, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), '意面', '意大利面', 'OTHER', 'g', 365, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT INTO food_weight_estimate (id, catalog_id, label, reference_grams, unit, source)
SELECT UUID_TO_BIN(UUID()), id, '1 个鸡蛋', 50.000, 'piece', '内置常见食材参考值'
FROM food_catalog WHERE canonical_name = '鸡蛋';
INSERT INTO food_weight_estimate (id, catalog_id, label, reference_grams, unit, source)
SELECT UUID_TO_BIN(UUID()), id, '1 盒北豆腐', 400.000, 'box', '内置常见食材参考值'
FROM food_catalog WHERE canonical_name = '北豆腐';
INSERT INTO food_weight_estimate (id, catalog_id, label, reference_grams, unit, source)
SELECT UUID_TO_BIN(UUID()), id, '1 杯无糖酸奶', 200.000, 'cup', '内置常见食材参考值'
FROM food_catalog WHERE canonical_name = '无糖酸奶';
INSERT INTO food_weight_estimate (id, catalog_id, label, reference_grams, unit, source)
SELECT UUID_TO_BIN(UUID()), id, '1 个小番茄', 18.000, 'piece', '内置常见食材参考值'
FROM food_catalog WHERE canonical_name = '小番茄';

INSERT INTO food_storage_profile (id, category, zone_kind, profile_version, unopened_hours, opened_hours, risk_coefficient, created_at, updated_at) VALUES
(UUID_TO_BIN(UUID()), 'VEGETABLE', 'FRESH', 1, 120, 72, 1.200, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), 'DAIRY', 'CHILL', 1, 168, 72, 1.300, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), 'MEAT_EGG', 'CHILL', 1, 72, 48, 1.500, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), 'SEAFOOD', 'CHILL', 1, 48, 24, 1.800, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), 'BEAN', 'CHILL', 1, 120, 72, 1.300, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), 'CONDIMENT', NULL, 1, 4320, 2160, 0.500, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), 'FRUIT', 'CHILL', 1, 168, 96, 1.000, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(UUID_TO_BIN(UUID()), 'OTHER', NULL, 1, NULL, NULL, 1.000, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
