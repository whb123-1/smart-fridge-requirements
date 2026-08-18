ALTER TABLE app_user
  ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER status;

CREATE TABLE user_preference (
  user_id BINARY(16) NOT NULL,
  tastes JSON NOT NULL,
  cuisines JSON NOT NULL,
  allergies JSON NOT NULL,
  dislikes JSON NOT NULL,
  dietary_goal VARCHAR(32) NULL,
  calorie_target INT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_preference_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recipe_source (
  id BINARY(16) NOT NULL,
  name VARCHAR(160) NOT NULL,
  source_type VARCHAR(24) NOT NULL,
  license_code VARCHAR(96) NOT NULL,
  attribution_text VARCHAR(500) NOT NULL,
  allowed_use VARCHAR(255) NOT NULL,
  source_version VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recipe_source_name_version (name, source_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recipe_import_job (
  id BINARY(16) NOT NULL,
  source_id BINARY(16) NOT NULL,
  requested_by BINARY(16) NOT NULL,
  status VARCHAR(24) NOT NULL,
  payload_json JSON NOT NULL,
  checksum CHAR(64) NOT NULL,
  imported_count INT NOT NULL DEFAULT 0,
  skipped_count INT NOT NULL DEFAULT 0,
  error_count INT NOT NULL DEFAULT 0,
  error_json JSON NULL,
  created_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recipe_import_source_checksum (source_id, checksum),
  KEY ix_recipe_import_status (status, created_at),
  CONSTRAINT fk_recipe_import_source FOREIGN KEY (source_id) REFERENCES recipe_source(id),
  CONSTRAINT fk_recipe_import_user FOREIGN KEY (requested_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recipe (
  id BINARY(16) NOT NULL,
  source_id BINARY(16) NULL,
  source_recipe_id VARCHAR(128) NULL,
  source_version VARCHAR(64) NULL,
  origin VARCHAR(24) NOT NULL,
  title VARCHAR(160) NOT NULL,
  summary TEXT NULL,
  cuisine VARCHAR(48) NULL,
  taste VARCHAR(48) NULL,
  goal VARCHAR(48) NULL,
  cook_minutes INT NOT NULL,
  servings DECIMAL(8,2) NOT NULL,
  calories_total DECIMAL(12,3) NULL,
  protein_total DECIMAL(12,3) NULL,
  fat_total DECIMAL(12,3) NULL,
  carbs_total DECIMAL(12,3) NULL,
  normalized_fingerprint CHAR(64) NOT NULL,
  review_status VARCHAR(24) NOT NULL DEFAULT 'APPROVED',
  attribution_text VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recipe_fingerprint (normalized_fingerprint),
  KEY ix_recipe_filter (review_status, cook_minutes, cuisine, taste),
  FULLTEXT KEY ft_recipe_text (title, summary),
  CONSTRAINT fk_recipe_source FOREIGN KEY (source_id) REFERENCES recipe_source(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recipe_component (
  id BINARY(16) NOT NULL,
  recipe_id BINARY(16) NOT NULL,
  catalog_id BINARY(16) NULL,
  name VARCHAR(120) NOT NULL,
  role VARCHAR(16) NOT NULL,
  quantity DECIMAL(12,3) NOT NULL,
  unit VARCHAR(24) NOT NULL,
  scaling_rule VARCHAR(16) NOT NULL,
  minimum_quantity DECIMAL(12,3) NULL,
  maximum_quantity DECIMAL(12,3) NULL,
  calories_per_100g DECIMAL(12,3) NULL,
  protein_per_100g DECIMAL(12,3) NULL,
  fat_per_100g DECIMAL(12,3) NULL,
  carbs_per_100g DECIMAL(12,3) NULL,
  sort_order INT NOT NULL,
  PRIMARY KEY (id),
  KEY ix_recipe_component_recipe (recipe_id, sort_order),
  KEY ix_recipe_component_catalog (catalog_id),
  CONSTRAINT fk_recipe_component_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id),
  CONSTRAINT fk_recipe_component_catalog FOREIGN KEY (catalog_id) REFERENCES food_catalog(id),
  CONSTRAINT chk_recipe_component_role CHECK (role IN ('PRIMARY','SIDE','SEASONING')),
  CONSTRAINT chk_recipe_component_scaling CHECK (scaling_rule IN ('LINEAR','BOUNDED','FIXED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recipe_step (
  id BINARY(16) NOT NULL,
  recipe_id BINARY(16) NOT NULL,
  step_number INT NOT NULL,
  instruction_text TEXT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recipe_step_number (recipe_id, step_number),
  CONSTRAINT fk_recipe_step_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recipe_knowledge_chunk (
  id BINARY(16) NOT NULL,
  recipe_id BINARY(16) NOT NULL,
  chunk_type VARCHAR(24) NOT NULL,
  content_text TEXT NOT NULL,
  source_version VARCHAR(64) NULL,
  attribution_text VARCHAR(500) NULL,
  embedding_model_version VARCHAR(96) NULL,
  index_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_recipe_chunk_recipe (recipe_id, chunk_type),
  FULLTEXT KEY ft_recipe_chunk_content (content_text),
  CONSTRAINT fk_recipe_chunk_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recipe_search_index_state (
  recipe_id BINARY(16) NOT NULL,
  mysql_indexed_at DATETIME(3) NULL,
  vector_indexed_at DATETIME(3) NULL,
  embedding_model_version VARCHAR(96) NULL,
  status VARCHAR(24) NOT NULL,
  failure_reason VARCHAR(1000) NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (recipe_id),
  CONSTRAINT fk_recipe_index_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recipe_bookmark (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  recipe_id BINARY(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recipe_bookmark_user_recipe (user_id, recipe_id),
  CONSTRAINT fk_recipe_bookmark_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_recipe_bookmark_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recipe_event (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  recipe_id BINARY(16) NOT NULL,
  event_type VARCHAR(24) NOT NULL,
  payload_json JSON NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_recipe_event_user_time (user_id, created_at DESC),
  CONSTRAINT fk_recipe_event_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_recipe_event_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE meal_record (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  recipe_id BINARY(16) NULL,
  meal_at DATETIME(3) NOT NULL,
  meal_type VARCHAR(24) NULL,
  name VARCHAR(160) NOT NULL,
  servings DECIMAL(8,2) NOT NULL,
  calories DECIMAL(12,3) NULL,
  protein DECIMAL(12,3) NULL,
  fat DECIMAL(12,3) NULL,
  carbs DECIMAL(12,3) NULL,
  estimated BOOLEAN NOT NULL DEFAULT FALSE,
  nutrition_source VARCHAR(64) NOT NULL,
  disclaimer VARCHAR(500) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_meal_user_time (user_id, meal_at DESC),
  CONSTRAINT fk_meal_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_meal_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO recipe_source
  (id,name,source_type,license_code,attribution_text,allowed_use,source_version,enabled,created_at,updated_at)
VALUES
  (UUID_TO_BIN(UUID()),'鲜知内置示例菜谱','CURATED','INTERNAL-DEMO','鲜知开发参考数据','站内展示与规则匹配','1',TRUE,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));

SET @source_id = (SELECT id FROM recipe_source WHERE name='鲜知内置示例菜谱' AND source_version='1');
SET @recipe_tomato_egg = UUID_TO_BIN(UUID());
INSERT INTO recipe
  (id,source_id,source_recipe_id,source_version,origin,title,summary,cuisine,taste,goal,cook_minutes,servings,calories_total,protein_total,fat_total,carbs_total,normalized_fingerprint,review_status,attribution_text,created_at,updated_at)
VALUES
  (@recipe_tomato_egg,@source_id,'demo-tomato-egg','1','CURATED','番茄炒蛋','简单家常菜，中等油盐。','家常菜','咸鲜','均衡',15,2,420,24,25,20,SHA2('番茄炒蛋|番茄|鸡蛋',256),'APPROVED','鲜知开发参考数据',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));

INSERT INTO recipe_component
  (id,recipe_id,catalog_id,name,role,quantity,unit,scaling_rule,minimum_quantity,maximum_quantity,calories_per_100g,protein_per_100g,fat_per_100g,carbs_per_100g,sort_order)
VALUES
  (UUID_TO_BIN(UUID()),@recipe_tomato_egg,NULL,'番茄','PRIMARY',300,'g','LINEAR',NULL,NULL,18,0.9,0.2,3.9,1),
  (UUID_TO_BIN(UUID()),@recipe_tomato_egg,NULL,'鸡蛋','PRIMARY',3,'piece','LINEAR',NULL,NULL,143,13,9.5,0.7,2),
  (UUID_TO_BIN(UUID()),@recipe_tomato_egg,NULL,'食用油','SEASONING',15,'ml','BOUNDED',8,25,884,0,100,0,3),
  (UUID_TO_BIN(UUID()),@recipe_tomato_egg,NULL,'盐','SEASONING',3,'g','BOUNDED',1,5,0,0,0,0,4);

INSERT INTO recipe_step (id,recipe_id,step_number,instruction_text) VALUES
  (UUID_TO_BIN(UUID()),@recipe_tomato_egg,1,'番茄切块，鸡蛋打散。'),
  (UUID_TO_BIN(UUID()),@recipe_tomato_egg,2,'先炒鸡蛋盛出，再炒番茄并混合调味。');

INSERT INTO recipe_knowledge_chunk
  (id,recipe_id,chunk_type,content_text,source_version,attribution_text,index_status,created_at,updated_at)
VALUES
  (UUID_TO_BIN(UUID()),@recipe_tomato_egg,'SUMMARY','番茄炒蛋：番茄与鸡蛋制作的十五分钟家常菜。','1','鲜知开发参考数据','MYSQL_INDEXED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));
