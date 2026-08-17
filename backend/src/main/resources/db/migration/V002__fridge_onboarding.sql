CREATE TABLE fridge (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  name VARCHAR(80) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  deleted_at DATETIME(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY ix_fridge_user (user_id, deleted_at),
  CONSTRAINT fk_fridge_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fridge_zone (
  id BINARY(16) NOT NULL,
  fridge_id BINARY(16) NOT NULL,
  kind VARCHAR(24) NOT NULL,
  name VARCHAR(48) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  target_temperature_c DECIMAL(5,2) NOT NULL,
  target_humidity_pct DECIMAL(5,2) NOT NULL,
  safe_temperature_min_c DECIMAL(5,2) NOT NULL,
  safe_temperature_max_c DECIMAL(5,2) NOT NULL,
  safe_humidity_min_pct DECIMAL(5,2) NOT NULL,
  safe_humidity_max_pct DECIMAL(5,2) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  deleted_at DATETIME(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fridge_zone_name (fridge_id, name),
  KEY ix_fridge_zone_fridge (fridge_id, enabled),
  CONSTRAINT fk_fridge_zone_fridge FOREIGN KEY (fridge_id) REFERENCES fridge(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sensor (
  id BINARY(16) NOT NULL,
  zone_id BINARY(16) NOT NULL,
  metric VARCHAR(16) NOT NULL,
  binding_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_BIND',
  source VARCHAR(24) NOT NULL DEFAULT 'LOGICAL_SLOT',
  slot_index INT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sensor_zone_metric_slot (zone_id, metric, slot_index),
  CONSTRAINT fk_sensor_zone FOREIGN KEY (zone_id) REFERENCES fridge_zone(id),
  CONSTRAINT chk_sensor_metric CHECK (metric IN ('TEMPERATURE', 'HUMIDITY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
