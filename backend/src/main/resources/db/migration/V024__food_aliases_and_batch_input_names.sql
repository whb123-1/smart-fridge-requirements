CREATE TABLE food_catalog_alias (
  id BINARY(16) NOT NULL,
  catalog_id BINARY(16) NOT NULL,
  alias VARCHAR(120) NOT NULL,
  normalized_alias VARCHAR(120) NOT NULL,
  source VARCHAR(32) NOT NULL DEFAULT 'CURATED',
  confidence DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
  approved BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_food_catalog_alias_normalized (normalized_alias),
  KEY ix_food_catalog_alias_catalog (catalog_id),
  CONSTRAINT fk_food_catalog_alias_catalog FOREIGN KEY (catalog_id) REFERENCES food_catalog(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE inventory_batch
  ADD COLUMN input_name VARCHAR(120) NULL AFTER item_id;

INSERT INTO food_catalog (id, canonical_name, aliases, category, default_unit, default_shelf_life_days, created_at, updated_at)
SELECT UUID_TO_BIN(UUID()), '番茄', '西红柿', 'VEGETABLE', 'g', 7, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM food_catalog WHERE canonical_name = '番茄');

INSERT INTO food_catalog_alias (id,catalog_id,alias,normalized_alias,source,confidence,approved,created_at,updated_at)
SELECT UUID_TO_BIN(UUID()), c.id, TRIM(a.alias), LOWER(REPLACE(TRIM(a.alias),' ','')), 'LEGACY', 1.0000, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM food_catalog c
JOIN JSON_TABLE(CONCAT('["', REPLACE(REPLACE(COALESCE(c.aliases,''),'\\','\\\\'), ',', '\",\"'), '"]'), '$[*]' COLUMNS(alias VARCHAR(120) PATH '$')) a
WHERE TRIM(a.alias) <> ''
  AND NOT EXISTS (SELECT 1 FROM food_catalog_alias x WHERE x.normalized_alias=LOWER(REPLACE(TRIM(a.alias),' ','')));

INSERT INTO food_catalog_alias (id,catalog_id,alias,normalized_alias,source,confidence,approved,created_at,updated_at)
SELECT UUID_TO_BIN(UUID()), c.id, '西红柿', '西红柿', 'CURATED', 1.0000, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM food_catalog c WHERE c.canonical_name='番茄'
  AND NOT EXISTS (SELECT 1 FROM food_catalog_alias x WHERE x.normalized_alias='西红柿');

UPDATE food_catalog_alias a
JOIN food_catalog c ON c.canonical_name = '番茄'
SET a.catalog_id = c.id, a.updated_at = UTC_TIMESTAMP(3)
WHERE a.normalized_alias IN ('番茄', '西红柿');

UPDATE inventory_batch b
JOIN inventory_item i ON i.id = b.item_id
SET b.input_name = i.display_name
WHERE b.input_name IS NULL;

UPDATE inventory_item i
JOIN food_catalog_alias a ON a.normalized_alias = LOWER(REPLACE(TRIM(i.display_name), ' ', '')) AND a.approved = TRUE
SET i.catalog_id = a.catalog_id
WHERE i.catalog_id IS NULL;

CREATE TEMPORARY TABLE inventory_item_merge_plan AS
SELECT id AS source_id,
       FIRST_VALUE(id) OVER (PARTITION BY user_id, fridge_id, catalog_id ORDER BY created_at, id) AS target_id,
       user_id
FROM inventory_item
WHERE catalog_id IS NOT NULL AND deleted_at IS NULL;

INSERT INTO audit_log (id,user_id,target_user_id,event_type,trace_id,metadata_json,created_at)
SELECT UUID_TO_BIN(UUID()), p.user_id, p.user_id, 'INVENTORY_CANONICAL_MERGE', UUID(),
       JSON_OBJECT('sourceItemId', BIN_TO_UUID(p.source_id), 'targetItemId', BIN_TO_UUID(p.target_id)), UTC_TIMESTAMP(3)
FROM inventory_item_merge_plan p
WHERE p.source_id <> p.target_id;

UPDATE inventory_batch b
JOIN inventory_item_merge_plan p ON p.source_id = b.item_id
SET b.item_id = p.target_id
WHERE p.source_id <> p.target_id;

UPDATE inventory_item i
JOIN inventory_item_merge_plan p ON p.source_id = i.id
SET i.deleted_at = COALESCE(i.deleted_at, UTC_TIMESTAMP(3)), i.updated_at = UTC_TIMESTAMP(3)
WHERE p.source_id <> p.target_id;

DROP TEMPORARY TABLE inventory_item_merge_plan;

CREATE TABLE recipe_web_source (
  id BINARY(16) NOT NULL,
  recipe_id BINARY(16) NOT NULL,
  title VARCHAR(300) NOT NULL,
  source_url VARCHAR(1000) NOT NULL,
  site VARCHAR(255) NOT NULL,
  summary TEXT NOT NULL,
  retrieved_at DATETIME(3) NOT NULL,
  source_version VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recipe_web_source_url (recipe_id, source_url(300)),
  KEY ix_recipe_web_source_recipe (recipe_id),
  CONSTRAINT fk_recipe_web_source_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
